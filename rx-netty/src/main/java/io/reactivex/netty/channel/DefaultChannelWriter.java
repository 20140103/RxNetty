package io.reactivex.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.netty.protocol.http.MultipleFutureListener;
import io.reactivex.netty.serialization.ByteTransformer;
import io.reactivex.netty.serialization.ContentTransformer;
import io.reactivex.netty.serialization.StringTransformer;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Nitesh Kant
 */
public class DefaultChannelWriter<O> implements ChannelWriter<O> {

    private final ChannelHandlerContext ctx;
    private final MultipleFutureListener unflushedWritesListener;

    public DefaultChannelWriter(ChannelHandlerContext context) {
        if (null == context) {
            throw new NullPointerException("Channel context can not be null.");
        }
        ctx = context;
        unflushedWritesListener = new MultipleFutureListener(ctx);
    }

    @Override
    public Observable<Void> writeAndFlush(O msg) {
        write(msg);
        return flush();
    }

    @Override
    public <R> Observable<Void> writeAndFlush(final R msg, final ContentTransformer<R> transformer) {
        write(msg, transformer);
        return flush();
    }

    @Override
    public void write(O msg) {
        writeOnChannel(msg);
    }

    @Override
    public <R> void write(R msg, ContentTransformer<R> transformer) {
        ByteBuf contentBytes = transformer.transform(msg, getAllocator());
        writeOnChannel(contentBytes);
    }

    @Override
    public void writeBytes(byte[] msg) {
        write(msg, new ByteTransformer());
    }

    @Override
    public void writeString(String msg) {
        write(msg, new StringTransformer());
    }

    @Override
    public Observable<Void> writeBytesAndFlush(byte[] msg) {
        write(msg, new ByteTransformer());
        return flush();
    }

    @Override
    public Observable<Void> writeStringAndFlush(String msg) {
        write(msg, new StringTransformer());
        return flush();
    }

    @Override
    public Observable<Void> flush() {
        ctx.flush();
        return unflushedWritesListener.listenForNextCompletion().take(1).flatMap(new Func1<ChannelFuture, Observable<Void>>() {
            @Override
            public Observable<Void> call(ChannelFuture future) {
                return Observable.empty();
            }
        });
    }

    @Override
    public void cancelPendingWrites(boolean mayInterruptIfRunning) {
        unflushedWritesListener.cancelPendingFutures(mayInterruptIfRunning);
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return ctx.alloc();
    }

    protected ChannelFuture writeOnChannel(Object msg) {
        ChannelFuture writeFuture = ctx.channel().write(msg); // Calling write on context will be wrong as the context will be of a component not necessarily, the head of the pipeline.
        unflushedWritesListener.listen(writeFuture);
        return writeFuture;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }
}
