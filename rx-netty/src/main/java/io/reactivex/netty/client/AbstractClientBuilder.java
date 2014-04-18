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
package io.reactivex.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Nitesh Kant
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractClientBuilder<I, O, B extends AbstractClientBuilder, C extends RxClient<I, O>> {

    private static final ScheduledExecutorService SHARED_IDLE_CLEANUP_SCHEDULER = Executors.newScheduledThreadPool(1);

    protected final RxClientImpl.ServerInfo serverInfo;
    protected final Bootstrap bootstrap;
    protected PipelineConfigurator<O, I> pipelineConfigurator;
    protected Class<? extends SocketChannel> socketChannel;
    protected EventLoopGroup eventLoopGroup;
    protected RxClient.ClientConfig clientConfig;
    protected ConnectionPool<O, I> connectionPool;
    protected PoolLimitDeterminationStrategy limitDeterminationStrategy;
    private long idleConnectionsTimeoutMillis = PoolConfig.DEFAULT_CONFIG.getMaxIdleTimeMillis();
    private ScheduledExecutorService poolIdleCleanupScheduler = SHARED_IDLE_CLEANUP_SCHEDULER;
    private PoolStatsProvider statsProvider = new PoolStatsImpl();

    protected AbstractClientBuilder(Bootstrap bootstrap, String host, int port) {
        this.bootstrap = bootstrap;
        serverInfo = new RxClientImpl.ServerInfo(host, port);
        clientConfig = RxClient.ClientConfig.Builder.newDefaultConfig();
        defaultChannelOptions();
    }

    protected AbstractClientBuilder(String host, int port) {
        this(new Bootstrap(), host, port);
    }

    public B defaultChannelOptions() {
        channelOption(ChannelOption.SO_KEEPALIVE, true);
        channelOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        return channelOption(ChannelOption.TCP_NODELAY, true);
    }

    public B pipelineConfigurator(PipelineConfigurator<O, I> pipelineConfigurator) {
        this.pipelineConfigurator = pipelineConfigurator;
        return returnBuilder();
    }

    public <T> B channelOption(ChannelOption<T> option, T value) {
        bootstrap.option(option, value);
        return returnBuilder();
    }

    public B channel(Class<? extends SocketChannel> socketChannel) {
        this.socketChannel = socketChannel;
        return returnBuilder();
    }

    public B eventloop(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return returnBuilder();
    }

    public B config(RxClient.ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        return returnBuilder();
    }

    public B connectionPool(ConnectionPool<O, I> pool) {
        connectionPool = pool;
        return returnBuilder();
    }

    public B withMaxConnections(int maxConnections) {
        limitDeterminationStrategy = new MaxConnectionsBasedStrategy(maxConnections);
        return returnBuilder();
    }

    public B withIdleConnectionsTimeoutMillis(long idleConnectionsTimeoutMillis) {
        this.idleConnectionsTimeoutMillis = idleConnectionsTimeoutMillis;
        return returnBuilder();
    }

    public B withConnectionPoolLimitStrategy(PoolLimitDeterminationStrategy limitDeterminationStrategy) {
        this.limitDeterminationStrategy = limitDeterminationStrategy;
        return returnBuilder();
    }

    public B withPoolIdleCleanupScheduler(ScheduledExecutorService poolIdleCleanupScheduler) {
        this.poolIdleCleanupScheduler = poolIdleCleanupScheduler;
        return returnBuilder();
    }

    public B withNoIdleConnectionCleanup() {
        poolIdleCleanupScheduler = null;
        return returnBuilder();
    }

    public B withPoolStatsProvider(PoolStatsProvider statsProvider) {
        this.statsProvider = statsProvider;
        return returnBuilder();
    }

    public C build() {
        if (null == socketChannel) {
            socketChannel = NioSocketChannel.class;
            if (null == eventLoopGroup) {
                eventLoopGroup = RxNetty.getRxEventLoopProvider().globalClientEventLoop();
            }
        }

        if (null == eventLoopGroup) {
            if (NioSocketChannel.class == socketChannel) {
                eventLoopGroup = RxNetty.getRxEventLoopProvider().globalClientEventLoop();
            } else {
                // Fail fast for defaults we do not support.
                throw new IllegalStateException("Specified a channel class but not the event loop group.");
            }
        }

        bootstrap.channel(socketChannel).group(eventLoopGroup);
        if (shouldCreateConnectionPool()) {
            PoolConfig poolConfig = new PoolConfig(idleConnectionsTimeoutMillis);
            connectionPool = new ConnectionPoolImpl<O, I>(poolConfig, limitDeterminationStrategy,
                                                          poolIdleCleanupScheduler, statsProvider);
        }
        return createClient();
    }

    private boolean shouldCreateConnectionPool() {
        return null == connectionPool && null != limitDeterminationStrategy
               || idleConnectionsTimeoutMillis != PoolConfig.DEFAULT_CONFIG.getMaxIdleTimeMillis();
    }

    protected abstract C createClient();

    @SuppressWarnings("unchecked")
    protected B returnBuilder() {
        return (B) this;
    }
}
