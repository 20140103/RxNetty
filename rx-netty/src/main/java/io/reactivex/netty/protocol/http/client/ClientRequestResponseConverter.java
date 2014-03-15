/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.protocol.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.LastHttpContent;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.ChannelPool;
import io.reactivex.netty.protocol.http.MultipleFutureListener;
import io.reactivex.netty.serialization.ContentTransformer;
import rx.Observer;
import rx.subjects.PublishSubject;

/**
 * A channel handler for {@link HttpClient} to convert netty's http request/response objects to {@link HttpClient}'s
 * request/response objects. It handles the following message types:
 *
 * <h2>Reading Objects</h2>
 * <ul>
 <li>{@link io.netty.handler.codec.http.HttpResponse: Converts it to {@link HttpClientResponse} </li>
 <li>{@link HttpContent}: Converts it to the content of the previously generated
{@link HttpClientResponse}</li>
 <li>{@link FullHttpResponse}: Converts it to a {@link HttpClientResponse} with pre-populated content observable.</li>
 <li>Any other object: Assumes that it is a transformed HTTP content & pass it through to the content observable.</li>
 </ul>
 *
 * <h2>Writing Objects</h2>
 * <ul>
 <li>{@link HttpClientRequest}: Converts it to a {@link io.netty.handler.codec.http.HttpRequest}</li>
 <li>{@link ByteBuf} to an {@link HttpContent}</li>
 <li>Pass through any other message type.</li>
 </ul>
 *
 * @author Nitesh Kant
 */
public class ClientRequestResponseConverter extends ChannelDuplexHandler {

    @SuppressWarnings("rawtypes") private final PublishSubject contentSubject; // The type of this subject can change at runtime because a user can convert the content at runtime.
    @SuppressWarnings("rawtypes") private Observer requestProcessingObserver;
    private ObservableConnection<?, ?> observableConnection;

    public ClientRequestResponseConverter() {
        contentSubject = PublishSubject.create();
    }

    private Long getKeepAliveTimeout(String keepAlive) {
        try {
            if (keepAlive != null) {
                String[] pairs = keepAlive.split(",");
                if (pairs != null) {
                    for (String pair: pairs) {
                        String[] nameValue = pair.trim().split("=");
                        if (nameValue != null && nameValue.length == 2 && nameValue[0].trim().equals("timeout")) {
                            return Long.valueOf(nameValue[1].trim());
                        }
                    }
                }
            } 
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Class<?> recievedMsgClass = msg.getClass();

        if (io.netty.handler.codec.http.HttpResponse.class.isAssignableFrom(recievedMsgClass)) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) msg;
            HttpHeaders headers = response.headers();
            String connectionHeaderValue = headers.get(HttpHeaders.Names.CONNECTION);
            if ("close".equals(connectionHeaderValue)) {
                ctx.attr(ChannelPool.IDLE_TIMEOUT_ATTR).set(Long.valueOf(0));
            } else {
                String keepAlive = headers.get("Keep-Alive");
                Long timeout = getKeepAliveTimeout(keepAlive);
                if (timeout != null) {
                    ctx.attr(ChannelPool.IDLE_TIMEOUT_ATTR).set(timeout);
                }
            }
            HttpClientResponse rxResponse = new HttpClientResponse(response, contentSubject);
            super.channelRead(ctx, rxResponse); // For FullHttpResponse, this assumes that after this call returns,
                                                // someone has subscribed to the content observable, if not the content will be lost.
        }

        if (HttpContent.class.isAssignableFrom(recievedMsgClass)) {// This will be executed if the incoming message is a FullHttpResponse or only HttpContent.
            ByteBuf content = ((ByteBufHolder) msg).content();
            if (content.isReadable()) {
                invokeContentOnNext(content);
            }
            if (LastHttpContent.class.isAssignableFrom(recievedMsgClass)) {
                if (null != requestProcessingObserver) {
                    requestProcessingObserver.onCompleted();
                }
                contentSubject.onCompleted();
                observableConnection.close();
            }
        } else if(!io.netty.handler.codec.http.HttpResponse.class.isAssignableFrom(recievedMsgClass)){
            invokeContentOnNext(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Class<?> recievedMsgClass = msg.getClass();

        if (HttpClientRequest.class.isAssignableFrom(recievedMsgClass)) {
            HttpClientRequest<?> rxRequest = (HttpClientRequest<?>) msg;
            if (rxRequest.getHeaders().hasContent()) {
                if (!rxRequest.getHeaders().isContentLengthSet()) {
                    rxRequest.getHeaders().add(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                }
                MultipleFutureListener allWritesListener = new MultipleFutureListener(promise);
                allWritesListener.listen(ctx.write(rxRequest.getNettyRequest()));
                if (rxRequest.hasContentSource()) {
                    ContentSource<?> contentSource;
                    if (rxRequest.hasRawContentSource()) {
                        contentSource = rxRequest.getRawContentSource();
                        @SuppressWarnings("rawtypes")
                        RawContentSource<?> rawContentSource = (RawContentSource) contentSource;
                        while (rawContentSource.hasNext()) {
                            @SuppressWarnings("rawtypes")
                            ContentTransformer transformer = rawContentSource.getTransformer();
                            @SuppressWarnings("unchecked")
                            ByteBuf byteBuf = transformer.transform(rawContentSource.next(), ctx.alloc());
                            allWritesListener.listen(ctx.write(byteBuf));
                        }
                    } else {
                        contentSource = rxRequest.getContentSource();
                        while (contentSource.hasNext()) {
                            allWritesListener.listen(ctx.write(contentSource.next()));
                        }
                    }
                }
            } else {
                if (!rxRequest.getHeaders().isContentLengthSet() && rxRequest.getMethod() != HttpMethod.GET) {
                    rxRequest.getHeaders().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
                }
                ctx.write(rxRequest.getNettyRequest(), promise);
            }
        } else {
            ctx.write(msg, promise); // pass through, since we do not understand this message.
        }
    }

    void setRequestProcessingObserver(@SuppressWarnings("rawtypes") Observer requestProcessingObserver) {
        this.requestProcessingObserver = requestProcessingObserver;
    }
    
    void setObservableConnection(ObservableConnection<?, ?> connection) {
        this.observableConnection = connection;
    }
    
    @SuppressWarnings("unchecked")
    private void invokeContentOnNext(Object nextObject) {
        try {
            contentSubject.onNext(nextObject);
        } catch (ClassCastException e) {
            contentSubject.onError(e);
        }
    }

}
