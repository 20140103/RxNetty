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
package io.reactivex.netty.protocol.tcp.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.EventExecutorGroup;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.DetachedChannelPipeline;
import io.reactivex.netty.channel.PrimitiveConversionHandler;
import io.reactivex.netty.codec.HandlerNames;
import io.reactivex.netty.metrics.MetricEventsSubject;
import io.reactivex.netty.protocol.tcp.ssl.DefaultSslCodec;
import io.reactivex.netty.protocol.tcp.ssl.SslCodec;
import io.reactivex.netty.server.ServerMetricsEvent;
import rx.exceptions.Exceptions;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;

import javax.net.ssl.SSLEngine;

/**
 * A collection of state that {@link TcpServer} holds.
 * This supports the copy-on-write semantics of {@link TcpServer}
 *
 * @param <R> The type of objects read from the server owning this state.
 * @param <W> The type of objects written to the server owning this state.
 */
public final class ServerState<R, W> {

    private final MetricEventsSubject<ServerMetricsEvent<?>> eventsSubject;

    private final int port;
    private final boolean secure;
    private final ServerBootstrap bootstrap;
    private final DetachedChannelPipeline detachedPipeline;

    private ServerState(int port, EventLoopGroup parent, EventLoopGroup child,
                        Class<? extends ServerChannel> channelClass) {
        this.port = port;
        bootstrap = new ServerBootstrap();
        bootstrap.childOption(ChannelOption.AUTO_READ, false); // by default do not read content unless asked.
        bootstrap.group(parent, child);
        bootstrap.channel(channelClass);
        eventsSubject = new MetricEventsSubject<ServerMetricsEvent<?>>();
        detachedPipeline = new DetachedChannelPipeline();
        detachedPipeline.addLast(HandlerNames.PrimitiveConverter.getName(), new Func0<ChannelHandler>() {
            @Override
            public ChannelHandler call() {
                return PrimitiveConversionHandler.INSTANCE; /*Sharable handler*/
            }
        });
        secure = false;
        bootstrap.childHandler(detachedPipeline.getChannelInitializer());
    }

    private ServerState(ServerState<R, W> toCopy, final ServerBootstrap newBootstrap) {
        port = toCopy.port;
        bootstrap = newBootstrap;
        detachedPipeline = toCopy.detachedPipeline;
        secure = toCopy.secure;
        bootstrap.childHandler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopy.eventsSubject.copy();
    }

    private ServerState(ServerState<?, ?> toCopy, final DetachedChannelPipeline newPipeline) {
        final ServerState<R, W> toCopyCast = toCopy.cast();
        port = toCopy.port;
        bootstrap = toCopyCast.bootstrap.clone();
        detachedPipeline = newPipeline;
        secure = toCopy.secure;
        bootstrap.childHandler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopyCast.eventsSubject.copy();
    }

    private ServerState(ServerState<?, ?> toCopy, final SslCodec sslCodec) {
        final ServerState<R, W> toCopyCast = toCopy.cast();
        port = toCopy.port;
        bootstrap = toCopyCast.bootstrap.clone();
        detachedPipeline = toCopy.detachedPipeline.copy().configure(sslCodec);
        secure = true;
        bootstrap.childHandler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopyCast.eventsSubject.copy();
    }

    public ServerState(ServerState<R, W> toCopy, final int port) {
        this.port = port;
        bootstrap = toCopy.bootstrap.clone();
        detachedPipeline = toCopy.detachedPipeline;
        secure = toCopy.secure;
        bootstrap.childHandler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopy.eventsSubject.copy();
    }

    public <T> ServerState<R, W> channelOption(ChannelOption<T> option, T value) {
        ServerState<R, W> copy = copyBootstrapOnly();
        copy.bootstrap.option(option, value);
        return copy;
    }

    public <T> ServerState<R, W> clientChannelOption(ChannelOption<T> option, T value) {
        ServerState<R, W> copy = copyBootstrapOnly();
        copy.bootstrap.childOption(option, value);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerFirst(String name, Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addFirst(name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerFirst(EventExecutorGroup group, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addFirst(group, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerLast(String name, Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addLast(name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerLast(EventExecutorGroup group, String name,
                                                              Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addLast(group, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerBefore(String baseName, String name,
                                                                Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addBefore(baseName, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerBefore(EventExecutorGroup group, String baseName,
                                                                String name, Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addBefore(group, baseName, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerAfter(String baseName, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addAfter(baseName, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> addChannelHandlerAfter(EventExecutorGroup group, String baseName,
                                                               String name, Func0<ChannelHandler> handlerFactory) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.addAfter(group, baseName, name, handlerFactory);
        return copy;
    }

    public <RR, WW> ServerState<RR, WW> pipelineConfigurator(Action1<ChannelPipeline> pipelineConfigurator) {
        ServerState<RR, WW> copy = copy();
        copy.detachedPipeline.configure(pipelineConfigurator);
        return copy;
    }

    public ServerState<R, W> enableWireLogging(final LogLevel wireLogginLevel) {
        return addChannelHandlerFirst(HandlerNames.WireLogging.getName(), new Func0<ChannelHandler>() {
            @Override
            public ChannelHandler call() {
                return new LoggingHandler(wireLogginLevel);
            }
        });
    }

    public ServerState<R, W> secure(Func1<ByteBufAllocator, SSLEngine> sslEngineFactory) {
        return secure(new DefaultSslCodec(sslEngineFactory));
    }

    public ServerState<R, W> secure(SSLEngine sslEngine) {
        return secure(new DefaultSslCodec(sslEngine));
    }

    public ServerState<R, W> secure(SslCodec sslCodec) {
        return new ServerState<R, W>(this, sslCodec);
    }

    public ServerState<R, W> unsafeSecure() {
        return secure(new DefaultSslCodec(new Func1<ByteBufAllocator, SSLEngine>() {
            @Override
            public SSLEngine call(ByteBufAllocator allocator) {
                SelfSignedCertificate ssc;
                try {
                    ssc = new SelfSignedCertificate();
                    return SslContext.newServerContext(ssc.certificate(), ssc.privateKey())
                                     .newEngine(allocator);
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        }));
    }

    public ServerState<R, W> serverPort(int port) {
        return new ServerState<R, W>(this, port);
    }

    public MetricEventsSubject<ServerMetricsEvent<?>> getEventsSubject() {
        return eventsSubject;
    }

    public static <RR, WW> ServerState<RR, WW> create(int port) {
        return create(port, RxNetty.getRxEventLoopProvider().globalServerEventLoop(), NioServerSocketChannel.class);
    }

    public static <RR, WW> ServerState<RR, WW> create(int port, EventLoopGroup group,
                                                      Class<? extends ServerChannel> channelClass) {
        return create(port, group, group, channelClass);
    }

    public static <RR, WW> ServerState<RR, WW> create(int port, EventLoopGroup parent, EventLoopGroup child,
                                                      Class<? extends ServerChannel> channelClass) {
        return new ServerState<>(port, parent, child, channelClass);
    }

    public int getServerPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    /*package private. Should not leak as it is mutable*/ ServerBootstrap getBootstrap() {
        return bootstrap;
    }

    private ServerState<R, W> copyBootstrapOnly() {
        return new ServerState<R, W>(this, bootstrap.clone());
    }

    private <RR, WW> ServerState<RR, WW> copy() {
        return new ServerState<RR, WW>(this, detachedPipeline.copy());
    }

    @SuppressWarnings("unchecked")
    private <RR, WW> ServerState<RR, WW> cast() {
        return (ServerState<RR, WW>) this;
    }
}
