package io.reactivex.netty.examples.java;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfiguratorComposite;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpRequest;
import io.reactivex.netty.protocol.http.server.HttpResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.text.sse.SSEEvent;
import rx.Notification;
import rx.Observable;
import rx.util.functions.Func1;

import java.util.concurrent.TimeUnit;

/**
 * @author Nitesh Kant
 */
public final class HttpSseServer {

    public static void main(String[] args) {
        final int port = 8080;

        RxNetty.createHttpServer(port,
                                 new RequestHandler<ByteBuf, SSEEvent>() {
                                     @Override
                                     public Observable<Void> handle(HttpRequest<ByteBuf> request,
                                                                    HttpResponse<SSEEvent> response) {
                                         return getIntervalObservable(response);
                                     }
                                 }, new PipelineConfiguratorComposite<HttpRequest<ByteBuf>, HttpResponse<SSEEvent>>(new PipelineConfigurator() {

            @Override
            public void configureNewPipeline(ChannelPipeline pipeline) {
                pipeline.addFirst(new LoggingHandler(LogLevel.ERROR));
            }
        }, PipelineConfigurators.<ByteBuf>sseServerConfigurator())).startAndWait();
    }


    private static Observable<Void> getIntervalObservable(final HttpResponse<SSEEvent> response) {
        return Observable.interval(1000, TimeUnit.MILLISECONDS)
                         .flatMap(new Func1<Long, Observable<Notification<Void>>>() {
                             @Override
                             public Observable<Notification<Void>> call(Long interval) {
                                 System.out.println("Writing SSE event for interval: " + interval);
                                 return response.writeContentAndFlush(new SSEEvent("1", "data: ", String.valueOf(
                                         interval))).materialize();
                             }
                         })
                         .takeWhile(new Func1<Notification<Void>, Boolean>() {
                             @Override
                             public Boolean call(Notification<Void> notification) {
                                 return !notification.isOnError();
                             }
                         })
                         .map(new Func1<Notification<Void>, Void>() {
                             @Override
                             public Void call(Notification<Void> notification) {
                                 return null;
                             }
                         });
    }
}
