/*
 * Copyright 2015 Netflix, Inc.
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
package io.reactivex.netty.protocol.http.serverNew;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.reactivex.netty.metrics.Clock;
import io.reactivex.netty.metrics.MetricEventsSubject;
import io.reactivex.netty.protocol.http.internal.AbstractHttpConnectionBridge;
import io.reactivex.netty.protocol.http.server.HttpServerMetricsEvent;
import io.reactivex.netty.server.ServerMetricsEvent;

public class HttpServerToConnectionBridge<C> extends AbstractHttpConnectionBridge<C> {

    private final MetricEventsSubject<ServerMetricsEvent<?>> eventsSubject;

    public HttpServerToConnectionBridge(MetricEventsSubject<ServerMetricsEvent<?>> eventsSubject) {
        this.eventsSubject = eventsSubject;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            if (!HttpHeaders.isContentLengthSet(response)) {
                // If there is no content length we need to specify the transfer encoding as chunked as we always
                // send data in multiple HttpContent.
                // On the other hand, if someone wants to not have chunked encoding, adding content-length will work
                // as expected.
                response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected boolean isHeaderMessage(Object nextItem) {
        return nextItem instanceof HttpRequest;
    }

    @Override
    protected Object newHttpObject(Object nextItem, Channel channel) {
        eventsSubject.onEvent(HttpServerMetricsEvent.REQUEST_HEADERS_RECEIVED);
        return new HttpServerRequestImpl<>((HttpRequest) nextItem, channel);
    }

    @Override
    protected void onContentReceived() {
        eventsSubject.onEvent(HttpServerMetricsEvent.REQUEST_CONTENT_RECEIVED);
    }

    @Override
    protected void onContentReceiveComplete(long receiveStartTimeMillis) {
        eventsSubject.onEvent(HttpServerMetricsEvent.REQUEST_RECEIVE_COMPLETE,
                              Clock.onEndMillis(receiveStartTimeMillis));
    }
}
