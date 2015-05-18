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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.codec.HandlerNames;
import io.reactivex.netty.metrics.Clock;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.protocol.http.server.HttpServerMetricsEvent;
import io.reactivex.netty.protocol.tcp.server.ConnectionHandler;
import io.reactivex.netty.protocol.tcp.server.TcpServer;
import io.reactivex.netty.protocol.tcp.ssl.SslCodec;
import io.reactivex.netty.server.ServerMetricsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public final class HttpServerImpl<I, O> extends HttpServer<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(HttpServerImpl.class);

    private final TcpServer<HttpServerRequest<I>, Object> server;

    private HttpServerImpl(TcpServer<HttpServerRequest<I>, Object> server) {
        this.server = server;
    }

    @Override
    public <T> HttpServer<I, O> channelOption(ChannelOption<T> option, T value) {
        return _copy(server.channelOption(option, value));
    }

    @Override
    public <T> HttpServer<I, O> clientChannelOption(ChannelOption<T> option, T value) {
        return _copy(server.clientChannelOption(option, value));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerFirst(String name, Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerFirst(name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerFirst(EventExecutorGroup group, String name,
                                                              Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerFirst(group, name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerLast(String name, Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerLast(name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerLast(EventExecutorGroup group, String name,
                                                             Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerLast(group, name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerBefore(String baseName, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerBefore(baseName, name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerBefore(EventExecutorGroup group, String baseName, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerBefore(group, baseName, name,
                                                                                  handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerAfter(String baseName, String name,
                                                              Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerAfter(baseName, name, handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> addChannelHandlerAfter(EventExecutorGroup group, String baseName, String name,
                                                              Func0<ChannelHandler> handlerFactory) {
        return _copy(HttpServerImpl.<II>castServer(server.addChannelHandlerAfter(group, baseName, name,
                                                                                 handlerFactory)));
    }

    @Override
    public <II, OO> HttpServer<II, OO> pipelineConfigurator(Action1<ChannelPipeline> pipelineConfigurator) {
        return _copy(HttpServerImpl.<II>castServer(server.pipelineConfigurator(pipelineConfigurator)));
    }

    @Override
    public HttpServer<I, O> secure(Func1<ByteBufAllocator, SSLEngine> sslEngineFactory) {
        return _copy(server.secure(sslEngineFactory));
    }

    @Override
    public HttpServer<I, O> secure(SSLEngine sslEngine) {
        return _copy(server.secure(sslEngine));
    }

    @Override
    public HttpServer<I, O> secure(SslCodec sslCodec) {
        return _copy(server.secure(sslCodec));
    }

    @Override
    public HttpServer<I, O> unsafeSecure() {
        return _copy(server.unsafeSecure());
    }

    @Override
    public HttpServer<I, O> enableWireLogging(LogLevel wireLoggingLevel) {
        return _copy(server.enableWireLogging(wireLoggingLevel));
    }

    @Override
    public int getServerPort() {
        return server.getServerPort();
    }

    @Override
    public void startAndWait(RequestHandler<I, O> requestHandler) {
        start(requestHandler);
        waitTillShutdown();
    }

    @Override
    public HttpServer<I, O> start(RequestHandler<I, O> requestHandler) {
        server.start(new HttpServerConnectionHandler(requestHandler));
        return this;
    }

    @Override
    public void shutdown() {
        server.shutdown();
    }

    @Override
    public void waitTillShutdown() {
        server.waitTillShutdown();
    }

    @Override
    public void waitTillShutdown(long duration, TimeUnit timeUnit) {
        server.waitTillShutdown(duration, timeUnit);
    }

    @Override
    public Subscription subscribe(MetricEventsListener<? extends ServerMetricsEvent<?>> listener) {
        return server.subscribe(listener);
    }

    static HttpServer<ByteBuf, ByteBuf> create(final TcpServer<ByteBuf, ByteBuf> tcpServer) {
        return new HttpServerImpl<>(
                tcpServer.<HttpServerRequest<ByteBuf>, Object>pipelineConfigurator(new Action1<ChannelPipeline>() {
                    @Override
                    public void call(ChannelPipeline pipeline) {
                        pipeline.addLast(HandlerNames.HttpServerEncoder.getName(), new HttpResponseEncoder());
                        pipeline.addLast(HandlerNames.HttpServerDecoder.getName(), new HttpRequestDecoder());
                        pipeline.addLast(new HttpServerToConnectionBridge<>(tcpServer.getEventsSubject()));
                    }
                }));
    }

    @SuppressWarnings("unchecked")
    private static <II> TcpServer<HttpServerRequest<II>, Object> castServer(TcpServer<?, ?> rawTypes) {
        return (TcpServer<HttpServerRequest<II>, Object>)rawTypes;
    }

    private static <II, OO> HttpServerImpl<II, OO> _copy(TcpServer<HttpServerRequest<II>, Object> newServer) {
        return new HttpServerImpl<>(newServer);
    }

    private class HttpServerConnectionHandler implements ConnectionHandler<HttpServerRequest<I>, Object> {

        private final RequestHandler<I, O> requestHandler;

        private HttpServerConnectionHandler(RequestHandler<I, O> requestHandler) {
            this.requestHandler = requestHandler;
        }

        @Override
        public Observable<Void> handle(final Connection<HttpServerRequest<I>, Object> newConnection) {
            return newConnection.getInput()
                                .lift(new RequestHandlingOperator(newConnection));
        }

        private class RequestHandlingOperator implements Operator<Void, HttpServerRequest<I>> {

            private final Connection<HttpServerRequest<I>, Object> newConnection;

            public RequestHandlingOperator(Connection<HttpServerRequest<I>, Object> newConnection) {
                this.newConnection = newConnection;
            }

            @Override
            public Subscriber<? super HttpServerRequest<I>> call(final Subscriber<? super Void> subscriber) {

                return new Subscriber<HttpServerRequest<I>>() {

                    @Override
                    public void onStart() {
                        if (!newConnection.unsafeNettyChannel().config().isAutoRead()) {
                            request(1);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        subscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        subscriber.onError(e);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public void onNext(HttpServerRequest<I> request) {

                        final long startTimeMillis = Clock.newStartTimeMillis();

                        server.getEventsSubject().onEvent(HttpServerMetricsEvent.NEW_REQUEST_RECEIVED);

                        final HttpServerResponse<O> response = newResponse(request);

                        final Subscription processingSubscription = handleRequest(request, startTimeMillis, response)
                                                                        .doOnTerminate(new Action0() {
                                                                            @Override
                                                                            public void call() {
                                                                                if (!newConnection.unsafeNettyChannel().config().isAutoRead()) {
                                                                                    request(1);
                                                                                }
                                                                            }
                                                                        })
                                                                        .subscribe();

                        newConnection.unsafeNettyChannel()
                                     .closeFuture()
                                     .addListener(new ChannelFutureListener() {
                                         @Override
                                         public void operationComplete(ChannelFuture future) throws Exception {
                                             processingSubscription.unsubscribe();
                                         }
                                     });
                    }
                };
            }

            @SuppressWarnings("unchecked")
            private Observable<Void> handleRequest(HttpServerRequest<I> request, final long startTimeMillis,
                                                   final HttpServerResponse<O> response) {
                Observable<Void> requestHandlingResult = null;
                try {

                    if (request.decoderResult().isSuccess()) {
                        requestHandlingResult = requestHandler.handle(request, response);
                    }

                    if(null == requestHandlingResult) {
                        /*If decoding failed an appropriate response status would have been set.
                          Otherwise, overwrite the status to 500*/
                        if (response.getStatus().equals(OK)) {
                            response.setStatus(INTERNAL_SERVER_ERROR);
                        }
                        requestHandlingResult = response.write(Observable.<O>empty());
                    }

                } catch (Throwable throwable) {
                    logger.error("Unexpected error while invoking HTTP user handler.", throwable);
                    /*If the headers are already written, then this will produce an error Observable.*/
                    requestHandlingResult = response.setStatus(INTERNAL_SERVER_ERROR)
                                                    .write(Observable.<O>empty());
                }

                return requestHandlingResult.lift(new Operator<Void, Void>() {
                    @Override
                    public Subscriber<? super Void> call(final Subscriber<? super Void> o) {
                        server.getEventsSubject().onEvent(HttpServerMetricsEvent.REQUEST_HANDLING_START,
                                                          Clock.onEndMillis(startTimeMillis));
                        return new Subscriber<Void>(o) {
                            @Override
                            public void onCompleted() {
                                server.getEventsSubject().onEvent(HttpServerMetricsEvent.REQUEST_HANDLING_SUCCESS,
                                                                  Clock.onEndMillis(startTimeMillis));
                                o.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                server.getEventsSubject().onEvent(HttpServerMetricsEvent.REQUEST_HANDLING_FAILED,
                                                                  Clock.onEndMillis(startTimeMillis), e);
                                logger.error("Unexpected error processing a request.", e);
                                o.onError(e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                                // No Op, its a void
                            }
                        };
                    }
                }).onErrorResumeNext(new Func1<Throwable, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Throwable throwable) {
                        return response.setStatus(INTERNAL_SERVER_ERROR)
                                       .dispose()
                                       .concatWith(newConnection.close())
                                       .onErrorResumeNext(Observable.<Void>empty());// Ignore errors on cleanup
                    }
                }).concatWith(request.dispose()/*Dispose request at the end of processing to discard content if not read*/
                ).concatWith(response.dispose()/*Dispose response at the end of processing to cleanup*/);

            }

            private HttpServerResponse<O> newResponse(HttpServerRequest<I> request) {
                HttpResponse responseHeaders;
                if (request.decoderResult().isFailure()) {
                    // As per the spec, we should send 414/431 for URI too long and headers too long, but we do not have
                    // enough info to decide which kind of failure has caused this error here.
                    responseHeaders = new DefaultHttpResponse(request.getHttpVersion(),
                                                              REQUEST_HEADER_FIELDS_TOO_LARGE);
                    responseHeaders.headers()
                                   .set(Names.CONNECTION, HttpHeaders.Values.CLOSE)
                                   .set(Names.CONTENT_LENGTH, 0);
                } else {
                    responseHeaders = new DefaultHttpResponse(HttpVersion.HTTP_1_1, OK);
                }
                HttpServerResponse<O> response = HttpServerResponseImpl.create(newConnection, responseHeaders);
                setConnectionHeader(request, response);
                return response;
            }

            private void setConnectionHeader(HttpServerRequest<I> request, HttpServerResponse<O> response) {
                if (request.isKeepAlive()) {
                    if (!request.getHttpVersion().isKeepAliveDefault()) {
                        // Avoid sending keep-alive header if keep alive is default.
                        // Issue: https://github.com/Netflix/RxNetty/issues/167
                        // This optimizes data transferred on the wire.

                        // Add keep alive header as per:
                        // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
                        request.setHeader(Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                    }
                } else {
                    response.setHeader(Names.CONNECTION, HttpHeaders.Values.CLOSE);
                }
            }

        }
    }
}
