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
package io.reactivex.netty.protocol.tcp.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ClientConnectionToChannelBridge;
import io.reactivex.netty.channel.DetachedChannelPipeline;
import io.reactivex.netty.channel.PrimitiveConversionHandler;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.MaxConnectionsBasedStrategy;
import io.reactivex.netty.client.PoolConfig;
import io.reactivex.netty.client.PoolLimitDeterminationStrategy;
import io.reactivex.netty.client.PreferCurrentEventLoopGroup;
import io.reactivex.netty.client.ServerPool;
import io.reactivex.netty.codec.HandlerNames;
import io.reactivex.netty.metrics.MetricEventsSubject;
import io.reactivex.netty.protocol.tcp.client.PreferCurrentEventLoopHolder.IdleConnectionsHolderFactory;
import io.reactivex.netty.protocol.tcp.ssl.DefaultSslCodec;
import io.reactivex.netty.protocol.tcp.ssl.SslCodec;
import rx.Observable;
import rx.exceptions.Exceptions;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import static io.reactivex.netty.client.PoolConfig.*;
import static io.reactivex.netty.codec.HandlerNames.*;

/**
 * A collection of state that {@link TcpClient} holds. This supports the copy-on-write semantics of {@link TcpClient}
 *
 * @param <W> The type of objects written to the client owning this state.
 * @param <R> The type of objects read from the client owning this state.
 *
 * @author Nitesh Kant
 */
class ClientState<W, R> {

    private final MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject;

    private ClientConnectionFactory<W, R> connectionFactory; // Not final since it depends on ClientState
    private final Bootstrap clientBootstrap;
    private final PoolConfig<W, R> poolConfig;
    private final DetachedChannelPipeline detachedPipeline;
    private final ServerPool<ClientMetricsEvent<?>> serverPool;
    private final boolean isSecure;

    private ClientState(EventLoopGroup group, Class<? extends Channel> channelClass,
                        MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject,
                        DetachedChannelPipeline detachedPipeline,
                        ServerPool<ClientMetricsEvent<?>> serverPool) {
        clientBootstrap = new Bootstrap();
        clientBootstrap.option(ChannelOption.AUTO_READ, false); // by default do not read content unless asked.
        clientBootstrap.group(group);
        clientBootstrap.channel(channelClass);
        poolConfig = null;
        this.eventsSubject = eventsSubject;
        isSecure = false;
        this.detachedPipeline = detachedPipeline;
        clientBootstrap.handler(detachedPipeline.getChannelInitializer());
        this.serverPool = serverPool;
    }

    private ClientState(ClientState<W, R> toCopy, Bootstrap newBootstrap) {
        clientBootstrap = newBootstrap;
        poolConfig = null == toCopy.poolConfig ? null : toCopy.poolConfig.copy();
        detachedPipeline = toCopy.detachedPipeline;
        isSecure = toCopy.isSecure;
        clientBootstrap.handler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopy.eventsSubject.copy();
        serverPool = toCopy.serverPool;
    }

    private ClientState(ClientState<W, R> toCopy, PoolConfig<W, R> poolConfig) {
        clientBootstrap = toCopy.clientBootstrap;
        this.poolConfig = poolConfig;
        detachedPipeline = toCopy.detachedPipeline;
        isSecure = toCopy.isSecure;
        clientBootstrap.handler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopy.eventsSubject.copy();
        serverPool = toCopy.serverPool;
    }

    private ClientState(ClientState<W, R> toCopy, SslCodec sslCodec) {
        clientBootstrap = toCopy.clientBootstrap;
        poolConfig = toCopy.poolConfig;
        eventsSubject = toCopy.eventsSubject.copy();
        detachedPipeline = toCopy.detachedPipeline.copy(new TailHandlerFactory<>(eventsSubject, true))
                                                  .configure(sslCodec);
        isSecure = true;
        clientBootstrap.handler(detachedPipeline.getChannelInitializer());
        serverPool = toCopy.serverPool;
    }

    private ClientState(ClientState<?, ?> toCopy, DetachedChannelPipeline newPipeline) {
        final ClientState<W, R> toCopyCast = toCopy.cast();
        clientBootstrap = toCopyCast.clientBootstrap.clone();
        poolConfig = null == toCopyCast.poolConfig ? null : toCopyCast.poolConfig.copy();
        detachedPipeline = newPipeline;
        isSecure = toCopy.isSecure;
        clientBootstrap.handler(detachedPipeline.getChannelInitializer());
        eventsSubject = toCopyCast.eventsSubject.copy();
        serverPool = toCopy.serverPool;
    }

    private ClientState(ClientState<?, ?> toCopy, ServerPool<ClientMetricsEvent<?>> newServerPool) {
        final ClientState<W, R> toCopyCast = toCopy.cast();
        clientBootstrap = toCopyCast.clientBootstrap;
        poolConfig = toCopyCast.poolConfig;
        detachedPipeline = toCopy.detachedPipeline;
        isSecure = toCopy.isSecure;
        eventsSubject = toCopyCast.eventsSubject.copy();
        serverPool = newServerPool;
    }

    public <T> ClientState<W, R> channelOption(ChannelOption<T> option, T value) {
        ClientState<W, R> copy = copyBootstrapOnly();
        copy.clientBootstrap.option(option, value);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerFirst(String name, Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addFirst(name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerFirst(EventExecutorGroup group, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addFirst(group, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerLast(String name, Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addLast(name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerLast(EventExecutorGroup group, String name,
                                                              Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addLast(group, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerBefore(String baseName, String name,
                                                                Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addBefore(baseName, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerBefore(EventExecutorGroup group, String baseName,
                                                                String name, Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addBefore(group, baseName, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerAfter(String baseName, String name,
                                                               Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addAfter(baseName, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> addChannelHandlerAfter(EventExecutorGroup group, String baseName,
                                                               String name, Func0<ChannelHandler> handlerFactory) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.addAfter(group, baseName, name, handlerFactory);
        return copy;
    }

    public <WW, RR> ClientState<WW, RR> pipelineConfigurator(Action1<ChannelPipeline> pipelineConfigurator) {
        ClientState<WW, RR> copy = copy();
        copy.detachedPipeline.configure(pipelineConfigurator);
        return copy;
    }

    public ClientState<W, R> secure(Func1<ByteBufAllocator, SSLEngine> sslEngineFactory) {
        return secure(new DefaultSslCodec(sslEngineFactory));
    }

    public ClientState<W, R> secure(SSLEngine sslEngine) {
        return secure(new DefaultSslCodec(sslEngine));
    }

    public ClientState<W, R> secure(SslCodec sslCodec) {
        ClientState<W, R> toReturn = new ClientState<W, R>(this, sslCodec);
        // Connection factory depends on bootstrap, so it should change whenever bootstrap changes.
        toReturn.connectionFactory = connectionFactory.copy(toReturn);
        return toReturn;
    }

    public ClientState<W, R> unsafeSecure() {
        return secure(new DefaultSslCodec(new Func1<ByteBufAllocator, SSLEngine>() {
            @Override
            public SSLEngine call(ByteBufAllocator allocator) {
                try {
                    return SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE)
                                     .newEngine(allocator);
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        }));
    }

    public ClientState<W, R> enableWireLogging(final LogLevel wireLogginLevel) {
        return addChannelHandlerFirst(HandlerNames.WireLogging.getName(),
                                      LoggingHandlerFactory.factories.get(wireLogginLevel));
    }

    public ClientState<W, R> maxConnections(int maxConnections) {
        PoolConfig<W, R> newPoolConfig;
        if (null != poolConfig) {
            newPoolConfig = poolConfig.maxConnections(maxConnections);
        } else {
            long maxIdleTimeMillis = DEFAULT_MAX_IDLE_TIME_MILLIS;
            newPoolConfig = new PoolConfig<W, R>(maxIdleTimeMillis,
                                                 Observable.timer(maxIdleTimeMillis, TimeUnit.MILLISECONDS),
                                                 new MaxConnectionsBasedStrategy(maxConnections),
                                                 newDefaultIdleConnectionsHolder(null));
        }
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, newPoolConfig);
        copyOrCreatePooledConnectionFactory(toReturn);
        return toReturn;
    }

    public ClientState<W, R> maxIdleTimeoutMillis(long maxIdleTimeoutMillis) {
        PoolConfig<W, R> newPoolConfig;
        if (null != poolConfig) {
            newPoolConfig = poolConfig.maxIdleTimeoutMillis(maxIdleTimeoutMillis);
        } else {
            newPoolConfig = new PoolConfig<W, R>(maxIdleTimeoutMillis,
                                                 Observable.timer(maxIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                                                 new MaxConnectionsBasedStrategy(),
                                                 newDefaultIdleConnectionsHolder(null));
        }
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, newPoolConfig);
        copyOrCreatePooledConnectionFactory(toReturn);
        return toReturn;
    }

    public ClientState<W, R> connectionPoolLimitStrategy(PoolLimitDeterminationStrategy strategy) {
        PoolConfig<W, R> newPoolConfig;
        if (null != poolConfig) {
            newPoolConfig = poolConfig.limitDeterminationStrategy(strategy);
        } else {
            long maxIdleTimeMillis = DEFAULT_MAX_IDLE_TIME_MILLIS;
            newPoolConfig = new PoolConfig<W, R>(maxIdleTimeMillis,
                                                 Observable.timer(maxIdleTimeMillis, TimeUnit.MILLISECONDS), strategy,
                                                 newDefaultIdleConnectionsHolder(null));
        }
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, newPoolConfig);
        copyOrCreatePooledConnectionFactory(toReturn);
        return toReturn;
    }

    public ClientState<W, R> idleConnectionCleanupTimer(Observable<Long> idleConnectionCleanupTimer) {
        PoolConfig<W, R> newPoolConfig;
        if (null != poolConfig) {
            newPoolConfig = poolConfig.idleConnectionsCleanupTimer(idleConnectionCleanupTimer);
        } else {
            newPoolConfig = new PoolConfig<W, R>(DEFAULT_MAX_IDLE_TIME_MILLIS, idleConnectionCleanupTimer,
                                                 new MaxConnectionsBasedStrategy(),
                                                 newDefaultIdleConnectionsHolder(null));
        }
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, newPoolConfig);
        copyOrCreatePooledConnectionFactory(toReturn);
        return toReturn;
    }

    public ClientState<W, R> noIdleConnectionCleanup() {
        return idleConnectionCleanupTimer(Observable.<Long>never());
    }

    public ClientState<W, R> noConnectionPooling() {
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, (PoolConfig<W, R>)null);
        toReturn.connectionFactory = new UnpooledClientConnectionFactory<>(toReturn);
        return toReturn;
    }

    public ClientState<W, R> remoteAddress(SocketAddress newAddress) {
        final ClientState<W, R> toReturn = new ClientState<W, R>(this, new IdentityServerPool(newAddress));
        toReturn.connectionFactory = connectionFactory.copy(toReturn);
        return toReturn;
    }

    public SocketAddress getRemoteAddress() {
        return serverPool.next().getAddress();
    }

    public boolean hasServerPool() {
        return !(serverPool instanceof IdentityServerPool);
    }

    public ServerPool<ClientMetricsEvent<?>> getServerPool() {
        return serverPool;
    }

    public ClientConnectionFactory<W, R> getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Returns connection pool configuration, if any.
     *
     * @return Connection pool configuration, if exists, else {@code null}
     */
    public PoolConfig<W, R> getPoolConfig() {
        return poolConfig;
    }

    public MetricEventsSubject<ClientMetricsEvent<?>> getEventsSubject() {
        return eventsSubject;
    }

    public static <WW, RR> ClientState<WW, RR> create() {
        return create(RxNetty.getRxEventLoopProvider().globalClientEventLoop(true), NioSocketChannel.class);
    }

    public static <WW, RR> ClientState<WW, RR> create(SocketAddress remoteAddress) {
        return create(RxNetty.getRxEventLoopProvider().globalClientEventLoop(true), NioSocketChannel.class, remoteAddress);
    }

    public static <WW, RR> ClientState<WW, RR> create(EventLoopGroup group, Class<? extends Channel> channelClass) {
        return create(group, channelClass, new InetSocketAddress("127.0.0.1", 80));
    }

    public static <WW, RR> ClientState<WW, RR> create(EventLoopGroup group, Class<? extends Channel> channelClass,
                                                      SocketAddress remoteAddress) {
        return create(group, channelClass, new IdentityServerPool(remoteAddress));
    }

    public static <WW, RR> ClientState<WW, RR> create(EventLoopGroup group, Class<? extends Channel> channelClass,
                                                      ServerPool<ClientMetricsEvent<?>> serverPool) {
        final MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject = new MetricEventsSubject<>();

        final TailHandlerFactory<WW, RR> tail = new TailHandlerFactory<>(eventsSubject, false);
        DetachedChannelPipeline detachedPipeline = new DetachedChannelPipeline(tail)
                .addLast(PrimitiveConverter.getName(), new Func0<ChannelHandler>() {
                    @Override
                    public ChannelHandler call() {
                        return PrimitiveConversionHandler.INSTANCE;
                    }
                });

        return create(detachedPipeline, eventsSubject, group, channelClass, serverPool);
    }

    public static <WW, RR> ClientState<WW, RR> create(ClientState<WW, RR> state,
                                                      ClientConnectionFactory<WW, RR> connectionFactory) {
        state.connectionFactory = connectionFactory;
        return state;
    }

    /*Visible for testing*/ static <WW, RR> ClientState<WW, RR> create(DetachedChannelPipeline detachedPipeline,
                                                                       MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject,
                                                                       EventLoopGroup group,
                                                                       Class<? extends Channel> channelClass,
                                                                       ServerPool<ClientMetricsEvent<?>> serverPool) {
        final ClientState<WW, RR> toReturn = new ClientState<WW, RR>(group, channelClass, eventsSubject,
                                                                     detachedPipeline, serverPool);
        toReturn.connectionFactory = new UnpooledClientConnectionFactory<>(toReturn);
        return toReturn;

    }

    /*package private. Should not leak as it is mutable*/ Bootstrap getBootstrap() {
        return clientBootstrap;
    }

    /*Visible for testing*/ DetachedChannelPipeline getDetachedPipeline() {
        return detachedPipeline;
    }

    private IdleConnectionsHolder<W, R> newDefaultIdleConnectionsHolder(final IdleConnectionsHolder<W, R> template) {
        if (clientBootstrap.group() instanceof PreferCurrentEventLoopGroup) {
            PreferCurrentEventLoopGroup pGroup = (PreferCurrentEventLoopGroup) clientBootstrap.group();
            return new PreferCurrentEventLoopHolder<W, R>(pGroup,
                                                          new IdleConnectionsHolderFactoryImpl<>(template));
        } else {
            return new FIFOIdleConnectionsHolder<>();
        }
    }

    private void copyOrCreatePooledConnectionFactory(ClientState<W, R> toReturn) {
        if (connectionFactory instanceof PooledClientConnectionFactory) {
            toReturn.connectionFactory = connectionFactory.copy(toReturn);
        } else {
            toReturn.connectionFactory = new PooledClientConnectionFactoryImpl<W, R>(toReturn);
        }
    }

    private ClientState<W, R> copyBootstrapOnly() {
        ClientState<W, R> toReturn = new ClientState<W, R>(this, clientBootstrap.clone());
        // Connection factory depends on bootstrap, so it should change whenever bootstrap changes.
        toReturn.connectionFactory = connectionFactory.copy(toReturn);
        return toReturn;
    }

    private <WW, RR> ClientState<WW, RR> copy() {
        ClientState<WW, RR> toReturn = new ClientState<WW, RR>(this, detachedPipeline.copy());
        // Connection factory depends on bootstrap, so it should change whenever bootstrap changes.
        toReturn.connectionFactory = connectionFactory.copy(toReturn);
        return toReturn;
    }

    @SuppressWarnings("unchecked")
    private <WW, RR> ClientState<WW, RR> cast() {
        return (ClientState<WW, RR>) this;
    }

    /**
     * {@link LoggingHandler} is a shaerable handler and hence need not be created for every client. This factory
     * manages a static map of log level -> instance which can be used directly instead of creating a new factoru per
     * client.
     */
    /*visible for testing*/static class LoggingHandlerFactory implements Func0<ChannelHandler> {

        /*visible for testing*/ static final EnumMap<LogLevel, LoggingHandlerFactory> factories =
                new EnumMap<LogLevel, LoggingHandlerFactory>(LogLevel.class);

        static {
            for (LogLevel logLevel : LogLevel.values()) {
                factories.put(logLevel, new LoggingHandlerFactory(logLevel));
            }
        }

        private final LoggingHandler loggingHandler;

        private LoggingHandlerFactory(LogLevel wireLogginLevel) {
            loggingHandler = new LoggingHandler(wireLogginLevel);
        }

        @Override
        public ChannelHandler call() {
            return loggingHandler;/*logging handler is shareable.*/
        }
    }

    private static class TailHandlerFactory<WW, RR> implements Func0<ChannelHandler> {

        private final MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject;
        private final boolean isSecure;

        public TailHandlerFactory(MetricEventsSubject<ClientMetricsEvent<?>> eventsSubject, boolean isSecure) {
            this.eventsSubject = eventsSubject;
            this.isSecure = isSecure;
        }

        @Override
        public ChannelHandler call() {
            return new ClientConnectionToChannelBridge<WW, RR>(eventsSubject, isSecure);
        }
    }

    private class IdleConnectionsHolderFactoryImpl<WW, RR> implements IdleConnectionsHolderFactory<WW, RR> {

        private final IdleConnectionsHolder<WW, RR> template;

        public IdleConnectionsHolderFactoryImpl(IdleConnectionsHolder<WW, RR> template) {
            this.template = template;
        }

        @Override
        public <WWW, RRR> IdleConnectionsHolderFactory<WWW, RRR> copy(ClientState<WWW, RRR> newState) {
            return new IdleConnectionsHolderFactoryImpl<>(template.copy(newState));
        }

        @Override
        public IdleConnectionsHolder<WW, RR> call() {
            if (null == template) {
                return new FIFOIdleConnectionsHolder<>();
            } else {
                return template.copy(ClientState.this.<WW, RR>cast());
            }
        }
    }

    /*Visible for testing*/static class IdentityServerPool implements ServerPool<ClientMetricsEvent<?>> {

        private final Server<ClientMetricsEvent<?>> theServer;

        IdentityServerPool(final SocketAddress socketAddress) {

            theServer = new Server<ClientMetricsEvent<?>>() {

                @Override
                public SocketAddress getAddress() {
                    return socketAddress;
                }

                @Override
                public Observable<Void> getLifecycle() {
                    return Observable.never();
                }

                @Override
                public void onEvent(ClientMetricsEvent<?> event, long duration, TimeUnit timeUnit, Throwable throwable,
                                    Object value) {
                    // No actions
                }

                @Override
                public void onCompleted() {
                    // No actions
                }

                @Override
                public void onSubscribe() {
                    // No actions
                }
            };
        }

        @Override
        public Server<ClientMetricsEvent<?>> next() {
            return theServer;
        }
    }
}
