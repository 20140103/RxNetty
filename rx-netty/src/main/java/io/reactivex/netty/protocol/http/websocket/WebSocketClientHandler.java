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

package io.reactivex.netty.protocol.http.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.reactivex.netty.protocol.http.websocket.frame.NettyToRxFrameDecoder;
import io.reactivex.netty.protocol.http.websocket.frame.RxToNettyFrameEncoder;

/**
 * {@link WebSocketClientHandler} orchestrates WebSocket handshake process and reconfigures
 * pipeline after the handshake is complete.
 *
 * @author Tomasz Bak
 */
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private final int maxFramePayloadLength;
    private final boolean messageAggregation;
    private ChannelPromise handshakeFuture;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker, int maxFramePayloadLength, boolean messageAggregation) {
        this.handshaker = handshaker;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.messageAggregation = messageAggregation;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);

            ChannelPipeline p = ctx.pipeline();
            ChannelHandlerContext nettyEncoderCtx = p.context(WebSocketFrameEncoder.class);
            p.addAfter(nettyEncoderCtx.name(), "rxnetty-to-netty-encoder", new RxToNettyFrameEncoder());
            ChannelHandlerContext nettyDecoderCtx = p.context(WebSocketFrameDecoder.class);
            p.addAfter(nettyDecoderCtx.name(), "netty-to-rxnetty-decoder", new NettyToRxFrameDecoder());
            if (messageAggregation) {
                p.addAfter(nettyDecoderCtx.name(), "websocket-frame-aggregator", new WebSocketFrameAggregator(maxFramePayloadLength));
            }
            p.remove(HttpObjectAggregator.class);
            p.remove(this);

            handshakeFuture.setSuccess();

            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}
