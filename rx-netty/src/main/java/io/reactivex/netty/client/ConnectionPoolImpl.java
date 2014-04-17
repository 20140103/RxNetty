package io.reactivex.netty.client;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nitesh Kant
 */
class ConnectionPoolImpl<I, O> implements ConnectionPool<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolImpl.class);

    private static final PoolExhaustedException POOL_EXHAUSTED_EXCEPTION = new PoolExhaustedException("Rx Connection Pool exhausted.");

    private final PoolStatsImpl stats;
    private final ConcurrentLinkedQueue<PooledConnection<I, O>> idleConnections;
    private ClientChannelFactory<I, O> channelFactory;
    private final PoolLimitDeterminationStrategy limitDeterminationStrategy;
    private final PublishSubject<StateChangeEvent> stateChangeObservable;
    private final PoolConfig poolConfig;
    private final ScheduledExecutorService cleanupScheduler;
    private final AtomicBoolean isShutdown = new AtomicBoolean();
    /*Nullable*/ private final ScheduledFuture<?> idleConnCleanupScheduleFuture;

    /**
     * Creates a new connection pool instance.
     *
     * @param poolConfig The pool configuration.
     * @param strategy Pool limit determination strategy. This can be {@code null}
     * @param cleanupScheduler Pool idle cleanup scheduler. This can be {@code null} which means there will be
     */
    ConnectionPoolImpl(PoolConfig poolConfig, PoolLimitDeterminationStrategy strategy,
                       ScheduledExecutorService cleanupScheduler) {
        this.poolConfig = poolConfig;
        this.cleanupScheduler = cleanupScheduler;

        long scheduleDurationMillis = Math.max(30, this.poolConfig.getMaxIdleTimeMillis()); // Ignore too agressive durations as they create a lot of thread spin.

        if (null != cleanupScheduler) {
            idleConnCleanupScheduleFuture = this.cleanupScheduler.scheduleWithFixedDelay(
                    new IdleConnectionsCleanupTask(), scheduleDurationMillis, scheduleDurationMillis, TimeUnit.MILLISECONDS);
        } else {
            idleConnCleanupScheduleFuture = null;
        }

        limitDeterminationStrategy = null == strategy ? new MaxConnectionsBasedStrategy() : strategy;
        stateChangeObservable = PublishSubject.create();
        stats = new PoolStatsImpl();
        stateChangeObservable.subscribe(stats);
        stateChangeObservable.subscribe(limitDeterminationStrategy);
        idleConnections = new ConcurrentLinkedQueue<PooledConnection<I, O>>();
        channelFactory = new NoOpClientChannelFactory<I, O>();
    }

    void setChannelFactory(ClientChannelFactory<I,O> channelFactory) { // There is a transitive circular dep b/w pool & factory hence we have to set the factory later.
        this.channelFactory = channelFactory;
    }

    @Override
    public Observable<ObservableConnection<I, O>> acquire(final PipelineConfigurator<I, O> pipelineConfigurator) {

        if (isShutdown.get()) {
            return Observable.error(new IllegalStateException("Connection pool is already shutdown."));
        }

        return Observable.create(new Observable.OnSubscribe<ObservableConnection<I, O>>() {
            @Override
            public void call(final Subscriber<? super ObservableConnection<I, O>> subscriber) {
                try {
                    stateChangeObservable.onNext(StateChangeEvent.onAcquireAttempted);
                    PooledConnection<I, O> idleConnection = getAnIdleConnection();

                    if (null != idleConnection) { // Found a usable connection
                        idleConnection.beforeReuse();
                        stateChangeObservable.onNext(StateChangeEvent.OnConnectionReuse);
                        stateChangeObservable.onNext(StateChangeEvent.onAcquireSucceeded);
                        subscriber.onNext(idleConnection);
                        subscriber.onCompleted();
                    } else if (limitDeterminationStrategy.acquireCreationPermit()) { // Check if it is allowed to create another connection.
                        /**
                         * Here we want to make sure that if the connection attempt failed, we should inform the strategy.
                         * Failure to do so, will leak the permits from the strategy. So, any code in this block MUST
                         * ALWAYS use this new subscriber instead of the original subscriber to send any callbacks.
                         */
                        Subscriber<ObservableConnection<I, O>> newConnectionSubscriber = newConnectionSubscriber(subscriber);
                        try {
                            channelFactory.connect(newConnectionSubscriber, pipelineConfigurator); // Manages the callbacks to the subscriber
                        } catch (Throwable throwable) {
                            newConnectionSubscriber.onError(throwable);
                        }
                    } else { // Pool Exhausted
                        stateChangeObservable.onNext(StateChangeEvent.onAcquireFailed);
                        subscriber.onError(POOL_EXHAUSTED_EXCEPTION);
                    }
                } catch (Throwable throwable) {
                    stateChangeObservable.onNext(StateChangeEvent.onAcquireFailed);
                    subscriber.onError(throwable);
                }
            }
        });
    }

    @Override
    public Observable<Void> release(PooledConnection<I, O> connection) {

        // Its legal to release after shutdown as it is not under anyones control when the connection returns. Its
        // usually user initiated.

        if (null == connection) {
            return Observable.error(new IllegalArgumentException("Returned a null connection to the pool."));
        }
        try {
            stateChangeObservable.onNext(StateChangeEvent.onReleaseAttempted);
            if (isShutdown.get() || !connection.isUsable()) {
                discardConnection(connection);
                stateChangeObservable.onNext(StateChangeEvent.onReleaseSucceeded);
                return Observable.empty();
            } else {
                idleConnections.add(connection);
                stateChangeObservable.onNext(StateChangeEvent.onReleaseSucceeded);
                return Observable.empty();
            }
        } catch (Throwable throwable) {
            stateChangeObservable.onNext(StateChangeEvent.onReleaseFailed);
            return Observable.error(throwable);
        }
    }

    @Override
    public Observable<Void> discard(PooledConnection<I, O> connection) {
        // Its legal to discard after shutdown as it is not under anyones control when the connection returns. Its
        // usually initiated by underlying connection close.

        if (null == connection) {
            return Observable.error(new IllegalArgumentException("Returned a null connection to the pool."));
        }

        boolean removed = idleConnections.remove(connection);

        if (removed) {
            discardConnection(connection);
        }

        return Observable.empty();
    }

    @Override
    public Observable<StateChangeEvent> stateChangeObservable() {
        return stateChangeObservable;
    }

    @Override
    public PoolStats getStats() {
        return stats;
    }

    @Override
    public void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            return;
        }

        if (null != idleConnCleanupScheduleFuture) {
            idleConnCleanupScheduleFuture.cancel(true);
        }
        PooledConnection<I, O> idleConnection = getAnIdleConnection();
        while (null != idleConnection) {
            discardConnection(idleConnection);
            idleConnection = getAnIdleConnection();
        }
        stateChangeObservable.onCompleted();
    }

    @Override
    public ObservableConnection<I, O> newConnection(ChannelHandlerContext ctx) {
        return new PooledConnection<I, O>(ctx, this, poolConfig.getMaxIdleTimeMillis());
    }

    private PooledConnection<I, O> getAnIdleConnection() {
        PooledConnection<I, O> idleConnection = idleConnections.poll();
        while (null != idleConnection) {
            if (!idleConnection.isUsable()) {
                discardConnection(idleConnection);
                idleConnection = idleConnections.poll();
            } else {
                break;
            }
        }
        return idleConnection;
    }

    private Observable<Void> discardConnection(PooledConnection<I, O> idleConnection) {
        stateChangeObservable.onNext(StateChangeEvent.OnConnectionEviction);
        return idleConnection.closeUnderlyingChannel();
    }

    private Subscriber<ObservableConnection<I, O>> newConnectionSubscriber(
            final Subscriber<? super ObservableConnection<I, O>> subscriber) {
        return Subscribers.create(new Action1<ObservableConnection<I, O>>() {
                                      @Override
                                      public void call(ObservableConnection<I, O> o) {
                                          stateChangeObservable.onNext(StateChangeEvent.NewConnectionCreated);
                                          stateChangeObservable.onNext(StateChangeEvent.onAcquireSucceeded);
                                          subscriber.onNext(o);
                                          subscriber.onCompleted(); // This subscriber is for "A" connection, so it should be completed.
                                      }
                                  }, new Action1<Throwable>() {
                                      @Override
                                      public void call(Throwable throwable) {
                                          stateChangeObservable.onNext(StateChangeEvent.ConnectFailed);
                                          subscriber.onError(throwable);
                                      }
                                  }
        );
    }

    private static class NoOpClientChannelFactory<I, O> implements ClientChannelFactory<I, O> {

        @Override
        public ChannelFuture connect(Subscriber<? super ObservableConnection<I, O>> subscriber,
                                     PipelineConfigurator<I, O> pipelineConfigurator) {
            throw new IllegalStateException("Client channel factory not set.");
        }
    }

    private class IdleConnectionsCleanupTask implements Runnable {

        @Override
        public void run() {
            try {
                Iterator<PooledConnection<I,O>> iterator = idleConnections.iterator(); // Weakly consistent iterator
                while (iterator.hasNext()) {
                    PooledConnection<I, O> idleConnection = iterator.next();
                    if (!idleConnection.isUsable()) {
                        iterator.remove();
                        discardConnection(idleConnection); // Don't use pool.discard() as that won't do anything if the
                                                           // connection isn't there in the idle queue, which is the case here.
                    }
                }
            } catch (Exception e) {
                logger.error("Exception in the idle connection cleanup task. This does NOT stop the next schedule of the task. ", e);
            }
        }
    }
}
