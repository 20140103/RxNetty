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
package io.reactivex.netty.protocol.http.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.protocol.http.TrailingHeaders;
import io.reactivex.netty.protocol.http.internal.AbstractHttpConnectionBridge.ConnectionInputSubscriber;
import io.reactivex.netty.protocol.http.internal.AbstractHttpConnectionBridge.TrailerProducer;
import io.reactivex.netty.protocol.http.internal.AbstractHttpConnectionBridgeTest.AbstractHttpConnectionBridgeMock.HttpObject;
import io.reactivex.netty.protocol.tcp.ConnectionInputSubscriberEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Matchers;
import org.mockito.Mockito;
import rx.Producer;
import rx.Subscriber;
import rx.observers.TestSubscriber;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class AbstractHttpConnectionBridgeTest {

    @Rule
    public final HandlerRule handlerRule = new HandlerRule();

    @Test
    public void testSetTransferEncoding() throws Exception {
        DefaultHttpRequest reqWithNoContentLength = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        handlerRule.channel.writeAndFlush(reqWithNoContentLength);

        assertThat("Unexpected outbound message count.", handlerRule.channel.outboundMessages(), hasSize(1));

        assertThat("Transfer encoding not set to chunked.", isTransferEncodingChunked(reqWithNoContentLength),
                   is(true));

        DefaultHttpRequest reqWithContentLength = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        setContentLength(reqWithContentLength, 100);

        handlerRule.channel.writeAndFlush(reqWithContentLength);

        /*One header from previous write*/
        assertThat("Unexpected outbound message count.", handlerRule.channel.outboundMessages(), hasSize(2));

        assertThat("Transfer encoding set to chunked when content length was set.",
                   isTransferEncodingChunked(reqWithContentLength), is(false));
    }

    @Test
    public void testWritePrimitives() throws Exception {
        handlerRule.channel.writeAndFlush("Hello");

        assertThat("Unexpected outbound message count.", handlerRule.channel.outboundMessages(), hasSize(1));
        assertThat("Unexpected message written.", handlerRule.channel.readOutbound(), instanceOf(ByteBuf.class));

        handlerRule.channel.writeAndFlush("Hello".getBytes());

        assertThat("Unexpected outbound message count.", handlerRule.channel.outboundMessages(), hasSize(1));
        assertThat("Unexpected message written.", handlerRule.channel.readOutbound(), instanceOf(ByteBuf.class));
    }

    @Test
    public void testConnInputSubscriberEvent() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();
    }

    @Test
    public void testHttpContentSubscriberEventWithNoContentInputSub() throws Exception {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<String>(subscriber));

        subscriber.assertTerminalEvent();

        assertThat("Subscriber did not get an error", subscriber.getOnErrorEvents(), hasSize(1));
        assertThat("Subscriber got an unexpected error", subscriber.getOnErrorEvents().get(0),
                   instanceOf(IllegalStateException.class));
    }

    @Test
    public void testHttpTrailerSubscriberEventWithNoContentInputSub() throws Exception {
        TestSubscriber<TrailingHeaders> subscriber = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(subscriber));

        subscriber.assertTerminalEvent();

        assertThat("Subscriber did not get an error", subscriber.getOnErrorEvents(), hasSize(1));
        assertThat("Subscriber got an unexpected error", subscriber.getOnErrorEvents().get(0),
                   instanceOf(IllegalStateException.class));
    }

    @Test
    public void testHttpContentSub() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        ProducerAwareSubscriber<String> subscriber = new ProducerAwareSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<String>(subscriber));

        subscriber.assertNoErrors();

        @SuppressWarnings("unchecked")
        Subscriber<String> contentSub = (Subscriber<String>) handlerRule.connInSub.getState().getContentSub();

        assertThat("Unexpected HTTP Content subscriber found", contentSub, equalTo((Subscriber<String>)subscriber));
        assertThat("Unexpected content subscriber producer.", subscriber.getProducer(),
                   equalTo(handlerRule.connInputProducerMock));

        subscriber.unsubscribe();

        subscriber.assertUnsubscribed();

        assertThat("Unsubscribing from HTTP content, did not unsubscribe from connection input.",
                   handlerRule.connInSub.isUnsubscribed(), is(true));
    }

    @Test
    public void testHttpTrailerSub() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        ProducerAwareSubscriber<TrailingHeaders> subscriber = new ProducerAwareSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(subscriber));

        subscriber.assertNoErrors();

        @SuppressWarnings("unchecked")
        Subscriber<TrailingHeaders> trailerSub =
                (Subscriber<TrailingHeaders>) handlerRule.connInSub.getState().getTrailerSub();

        assertThat("Unexpected HTTP trailer subscriber found", trailerSub,
                   equalTo((Subscriber<TrailingHeaders>) subscriber));

        assertThat("Unexpected trailer subscriber producer.", subscriber.getProducer(),
                   instanceOf(TrailerProducer.class));

        final TrailerProducer trailerProducer = (TrailerProducer) subscriber.getProducer();

        assertThat("Unexpected trailer subscriber producer delegate.", trailerProducer.getDelegateProducer(),
                   equalTo(handlerRule.connInputProducerMock));
    }

    @Test
    public void testContentArrivedBeforeSubscription() throws Exception {
        handlerRule.channel.config().setAutoRead(false);

        handlerRule.setupAndAssertConnectionInputSub();

        handlerRule.connInSub.onNext(new DefaultLastHttpContent());/*Simulating content read on channel*/

        TestSubscriber<String> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<String>(contentSub));

        contentSub.assertTerminalEvent();

        assertThat("Content received on delayed subscription.", contentSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on delayed subscription.", contentSub.getOnErrorEvents(), hasSize(1));

        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        trailerSub.assertTerminalEvent();

        assertThat("Trailers received on delayed subscription.", trailerSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on delayed subscription.", trailerSub.getOnErrorEvents(), hasSize(1));
    }

    @Test
    public void testLazyContentAndTrailerSubWithAutoReadOn() throws Exception {
        handlerRule.channel.config().setAutoRead(true);

        handlerRule.setupAndAssertConnectionInputSub();

        /*Request sent, no content/trailer sub registered, will cause error on sub.*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        TestSubscriber<String> contentSub = new TestSubscriber<>();
        /*Lazy subscription*/
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<String>(contentSub));

        contentSub.assertTerminalEvent();

        assertThat("Content received on lazy subscription.", contentSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on lazy subscription.", contentSub.getOnErrorEvents(), hasSize(1));

        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        /*Lazy subscription*/
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        trailerSub.assertTerminalEvent();

        assertThat("Trailers received on lazy subscription.", trailerSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on lazy subscription.", trailerSub.getOnErrorEvents(), hasSize(1));
    }

    @Test
    public void testLazyContentAndTrailerSubWithAutoReadOff() throws Exception {
        handlerRule.channel.config().setAutoRead(false);

        handlerRule.setupAndAssertConnectionInputSub();

        /*Request sent, after this it will expect the subscriber to be registered before content arrives..*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        /*Content sent but no subscriber.*/
        handlerRule.connInSub.onNext(new DefaultHttpContent(Unpooled.buffer().writeBytes("Hello".getBytes())));

        TestSubscriber<String> contentSub = new TestSubscriber<>();
        /*Content already sent, lazy sub now.*/
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<String>(contentSub));

        contentSub.assertTerminalEvent();

        assertThat("Content received on lazy subscription.", contentSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on lazy subscription.", contentSub.getOnErrorEvents(), hasSize(1));

        handlerRule.connInSub.onNext(new DefaultLastHttpContent());/*Simulate completion.*/

        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        /*Content already completed, lazy sub now.*/
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        trailerSub.assertTerminalEvent();

        assertThat("Trailers received on lazy subscription.", trailerSub.getOnNextEvents(), is(empty()));
        assertThat("Error not received on lazy subscription.", trailerSub.getOnErrorEvents(), hasSize(1));
    }

    @Test
    public void testHttpChunked() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        /*Eager content subscription*/
        TestSubscriber<ByteBuf> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub));

        /*Eager trailer subscription*/
        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        /*Headers sent*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        ByteBuf content1 = Unpooled.buffer().writeBytes("Hello".getBytes());
        ByteBuf content2 = Unpooled.buffer().writeBytes("Hello2".getBytes());
        ByteBuf contentLast = Unpooled.buffer().writeBytes("Hello3".getBytes());

        /*Content 1 sent.*/
        handlerRule.connInSub.onNext(new DefaultHttpContent(content1));
        /*Content 2 sent.*/
        handlerRule.connInSub.onNext(new DefaultHttpContent(content2));

        DefaultLastHttpContent trailers = new DefaultLastHttpContent(contentLast);
        String trailer1Name = "foo";
        String trailer1Value = "bar";
        trailers.trailingHeaders().add(trailer1Name, trailer1Value);

        /*trailers with content*/
        handlerRule.connInSub.onNext(trailers);

        contentSub.assertTerminalEvent();
        contentSub.assertNoErrors();

        assertThat("Unexpected content chunks.", contentSub.getOnNextEvents(), hasSize(3));
        assertThat("Unexpected content chunks.", contentSub.getOnNextEvents(), contains(content1, content2,
                                                                                        contentLast));

        trailerSub.assertTerminalEvent();
        trailerSub.assertNoErrors();

        assertThat("Unexpected trailer.", trailerSub.getOnNextEvents(), hasSize(1));

        TrailingHeaders trailingHeaders = trailerSub.getOnNextEvents().get(0);
        assertThat("Unexpected trailing header name.", trailingHeaders.getAllHeaderValues(trailer1Name), hasSize(1));
        assertThat("Unexpected trailing header name.", trailingHeaders.getAllHeaderValues(trailer1Name).get(0),
                   equalTo(trailer1Value));
    }

    @Test
    public void testClose() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        /*Eager content subscription*/
        TestSubscriber<ByteBuf> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub));

        /*Eager trailer subscription*/
        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        /*Headers sent*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        /*Close before response complete*/
        handlerRule.channel.close();

        handlerRule.headerSub.assertTerminalEvent();
        contentSub.assertTerminalEvent();
        trailerSub.assertTerminalEvent();

        assertThat("No error to header subscriber on close.", handlerRule.headerSub.getOnErrorEvents(), hasSize(1));
        assertThat("No error to content subscriber on close.", contentSub.getOnErrorEvents(), hasSize(1));
        assertThat("No error to trailer subscriber on close.", trailerSub.getOnErrorEvents(), hasSize(1));

        assertThat("Close before complete did not get invoked.", handlerRule.handler.closedBeforeReceive, is(true));
    }

    @Test
    public void testHeaderUnsubscribeBeforeHeaderReceive() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        handlerRule.headerSub.unsubscribe();

        assertThat("Connection input not unsubscribed.", handlerRule.connInSub.isUnsubscribed(), is(true));
    }

    @Test
    public void testHeaderUnsubscribeAfterHeaderReceive() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();
        /*Headers sent*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        handlerRule.headerSub.unsubscribe();

        assertThat("Connection input unsubscribed post headers.", handlerRule.connInSub.isUnsubscribed(), is(false));
    }

    @Test
    public void testConnectionInputCompleteWithNoHeaders() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        /*Eager content subscription*/
        TestSubscriber<ByteBuf> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub));

        /*Eager trailer subscription*/
        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        handlerRule.connInSub.onCompleted();

        handlerRule.headerSub.assertTerminalEvent();
        handlerRule.headerSub.assertNoErrors();

        contentSub.assertTerminalEvent();
        contentSub.assertNoErrors();

        trailerSub.assertTerminalEvent();
        trailerSub.assertNoErrors();
    }

    @Test
    public void testConnectionInputCompletePostHeaders() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        /*Eager content subscription*/
        TestSubscriber<ByteBuf> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub));

        /*Eager trailer subscription*/
        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        /*Headers sent*/
        handlerRule.connInSub.onNext(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        handlerRule.headerSub.assertNoErrors();
        assertThat("Header subscriber did not get the headers.", handlerRule.headerSub.getOnNextEvents(), hasSize(1));
        /*Look only for one HTTP message*/
        handlerRule.headerSub.unsubscribe();
        assertThat("Content subscriber unsubscribed post header unsubscribe.", contentSub.isUnsubscribed(), is(false));
        assertThat("Trailer subscriber unsubscribed post header unsubscribe.", trailerSub.isUnsubscribed(), is(false));

        handlerRule.connInSub.onCompleted();

        contentSub.assertTerminalEvent();
        assertThat("Content subscriber did not get an error.", contentSub.getOnErrorEvents(), hasSize(1));

        trailerSub.assertTerminalEvent();
        assertThat("Trailer subscriber did not get an error.", trailerSub.getOnErrorEvents(), hasSize(1));
    }

    @Test
    public void testMultiSubscribers() throws Exception {
        handlerRule.setupAndAssertConnectionInputSub();

        /*Eager content subscription*/
        TestSubscriber<ByteBuf> contentSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub));

        @SuppressWarnings("unchecked")
        Subscriber<ByteBuf> contentSubFound = (Subscriber<ByteBuf>) handlerRule.connInSub.getState().getContentSub();

        assertThat("Unexpected HTTP Content subscriber found", contentSubFound,
                   equalTo((Subscriber<ByteBuf>) contentSub));

        /*Eager trailer subscription*/
        TestSubscriber<TrailingHeaders> trailerSub = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub));

        @SuppressWarnings("unchecked")
        Subscriber<TrailingHeaders> trailerSubFound =
                (Subscriber<TrailingHeaders>) handlerRule.connInSub.getState().getTrailerSub();

        assertThat("Unexpected HTTP trailer subscriber found", trailerSubFound,
                   equalTo((Subscriber<TrailingHeaders>) trailerSub));

        contentSub.assertNoErrors();
        trailerSub.assertNoErrors();

        /*Second active subscription*/
        TestSubscriber<ByteBuf> contentSub2 = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpContentSubscriberEvent<ByteBuf>(contentSub2));

        /*Second active subscription*/
        TestSubscriber<TrailingHeaders> trailerSub2 = new TestSubscriber<>();
        handlerRule.channel.pipeline().fireUserEventTriggered(new HttpTrailerSubscriberEvent(trailerSub2));

        contentSub2.assertTerminalEvent();
        assertThat("Second content subscriber did not get an error.", contentSub2.getOnErrorEvents(), hasSize(1));

        trailerSub2.assertTerminalEvent();
        assertThat("Second trailer subscriber did not get an error.", trailerSub2.getOnErrorEvents(), hasSize(1));
    }

    public static class HandlerRule extends ExternalResource {

        private Connection<String, String> connMock;
        private EmbeddedChannel channel;
        private AbstractHttpConnectionBridgeMock handler;
        private EventCatcher eventCatcher;
        private ConnectionInputSubscriber connInSub;
        private Producer connInputProducerMock;
        private ProducerAwareSubscriber<HttpObject> headerSub;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    handler = new AbstractHttpConnectionBridgeMock(HttpRequest.class);
                    eventCatcher = new EventCatcher();
                    channel = new EmbeddedChannel(handler, eventCatcher);
                    @SuppressWarnings("unchecked")
                    Connection<String, String> connMock = Mockito.mock(Connection.class);
                    Mockito.when(connMock.getNettyChannel()).thenReturn(channel);

                    HandlerRule.this.connMock = connMock;
                    base.evaluate();
                }
            };
        }

        protected void setupAndAssertConnectionInputSub() {
            headerSub = new ProducerAwareSubscriber<>();

            @SuppressWarnings({"rawtypes", "unchecked"})
            ConnectionInputSubscriberEvent evt = new ConnectionInputSubscriberEvent(headerSub, connMock);

            channel.pipeline().fireUserEventTriggered(evt);

            assertThat("Handler did not pass the event.", eventCatcher.events, hasSize(1));
            assertThat("Handler did not modify the event.", eventCatcher.events, not(contains((Object) evt)));

            Object eventCaught = eventCatcher.events.get(0);

            assertThat("Unexpected propagated event.", eventCaught, instanceOf(ConnectionInputSubscriberEvent.class));

            @SuppressWarnings({"rawtypes", "unchecked"})
            ConnectionInputSubscriberEvent modEvt = (ConnectionInputSubscriberEvent) eventCaught;

            assertThat("Unexpected propagated event subscriber.", modEvt.getSubscriber(),
                       instanceOf(ConnectionInputSubscriber.class));

            @SuppressWarnings("unchecked")
            ConnectionInputSubscriber connInSub = (ConnectionInputSubscriber) modEvt.getSubscriber();
            this.connInSub = connInSub;

            assertThat("Channel not set in the subscriber.", connInSub.getChannel(), is(notNullValue()));
            assertThat("Unexpected channel set in the subscriber.", connInSub.getChannel(), equalTo((Channel)channel));

            @SuppressWarnings("unchecked")
            Subscriber<HttpObject> headerSub = (Subscriber<HttpObject>) connInSub.getState().getHeaderSub();

            assertThat("Unexpected header subscriber.", headerSub, is((Subscriber<HttpObject>) this.headerSub));

            connInputProducerMock = Mockito.mock(Producer.class);
            connInSub.setProducer(connInputProducerMock);

            assertThat("Header subscriber producer not set.", this.headerSub.getProducer(),
                       equalTo(connInputProducerMock));

            Mockito.verify(connInputProducerMock).request(Matchers.anyLong());
        }

    }

    private static class ProducerAwareSubscriber<T> extends TestSubscriber<T> {

        private Producer producer;

        @Override
        public void setProducer(Producer producer) {
            this.producer = producer;
            super.setProducer(producer);
        }

        public Producer getProducer() {
            return producer;
        }
    }

    private static class EventCatcher extends ChannelDuplexHandler {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            events.add(evt);
            super.userEventTriggered(ctx, evt);
        }
    }

    public static class AbstractHttpConnectionBridgeMock extends AbstractHttpConnectionBridge<String> {

        private final Class<?> headerMsgClass;
        private volatile boolean closedBeforeReceive;

        public AbstractHttpConnectionBridgeMock(Class<?> headerMsgClass) {
            this.headerMsgClass = headerMsgClass;
        }

        @Override
        protected boolean isHeaderMessage(Object nextItem) {
            return headerMsgClass.isAssignableFrom(nextItem.getClass());
        }

        @Override
        protected Object newHttpObject(Object nextItem, Channel channel) {
            return new HttpObject();
        }

        @Override
        protected void onContentReceived() {
            // No Op
        }

        @Override
        protected void onContentReceiveComplete(long receiveStartTimeMillis) {
            // No Op
        }

        @Override
        protected void onClosedBeforeReceiveComplete(ConnectionInputSubscriber connectionInputSubscriber) {
            closedBeforeReceive = true;
        }

        public static class HttpObject {
        }
    }
}
