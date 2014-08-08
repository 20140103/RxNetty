package io.reactivex.netty.protocol.http.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import io.reactivex.netty.metrics.MetricEventsSubject;
import io.reactivex.netty.protocol.http.websocket.WebSocketServerMetricsHandlers.ServerReadMetricsHandler;
import io.reactivex.netty.protocol.http.websocket.WebSocketServerMetricsHandlers.ServerWriteMetricsHandler;
import io.reactivex.netty.server.ServerMetricsEvent;

import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * {@link WebSocketServerHandler} orchestrates WebSocket handshake process and reconfigures
 * pipeline after the handshake is complete.
 *
 * @author Tomasz Bak
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketServerHandshakerFactory handshakeHandlerFactory;
    private ChannelPromise handshakeFuture;
    private final int maxFramePayloadLength;
    private final boolean messageAggregator;
    private final MetricEventsSubject<ServerMetricsEvent<?>> eventsSubject;

    public WebSocketServerHandler(WebSocketServerHandshakerFactory handshakeHandlerFactory,
                                  int maxFramePayloadLength,
                                  boolean messageAggregator,
                                  MetricEventsSubject<ServerMetricsEvent<?>> eventsSubject) {
        this.handshakeHandlerFactory = handshakeHandlerFactory;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.messageAggregator = messageAggregator;
        this.eventsSubject = eventsSubject;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
            updatePipeline(ctx);
            handshakeFuture.setSuccess();
            eventsSubject.onEvent(WebSocketServerMetricsEvent.HANDSHAKE_PROCESSED);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void updatePipeline(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        ChannelHandlerContext nettyEncoderCtx = p.context(WebSocketFrameEncoder.class);
        p.addAfter(nettyEncoderCtx.name(), "websocket-write-metrics", new ServerWriteMetricsHandler(eventsSubject));
        ChannelHandlerContext nettyDecoderCtx = p.context(WebSocketFrameDecoder.class);
        p.addAfter(nettyDecoderCtx.name(), "websocket-read-metrics", new ServerReadMetricsHandler(eventsSubject));
        if (messageAggregator) {
            p.addAfter("websocket-read-metrics", "websocket-frame-aggregator", new WebSocketFrameAggregator(maxFramePayloadLength));
        }
        p.remove(this);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Handle a bad request.
        if (!req.getDecoderResult().isSuccess()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            eventsSubject.onEvent(WebSocketServerMetricsEvent.HANDSHAKE_FAILURE);
            return;
        }

        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            eventsSubject.onEvent(WebSocketServerMetricsEvent.HANDSHAKE_FAILURE);
            return;
        }

        // Handshake
        WebSocketServerHandshaker handshaker = handshakeHandlerFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private static void sendHttpResponse(
            ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            setContentLength(res, res.content().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
