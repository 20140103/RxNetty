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
package io.reactivex.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.RxEventLoopProvider;
import io.reactivex.netty.channel.SingleNioLoopProvider;
import io.reactivex.netty.client.ClientBuilder;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerBuilder;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.udp.client.UdpClientBuilder;
import io.reactivex.netty.server.RxServer;
import io.reactivex.netty.server.ServerBuilder;
import io.reactivex.netty.protocol.udp.server.UdpServer;
import io.reactivex.netty.protocol.udp.server.UdpServerBuilder;

import static io.reactivex.netty.client.MaxConnectionsBasedStrategy.DEFAULT_MAX_CONNECTIONS;

public final class RxNetty {

    private static volatile RxEventLoopProvider rxEventLoopProvider = new SingleNioLoopProvider();

    private RxNetty() {
    }

    public static <I, O> UdpServer<I, O> createUdpServer(final int port, PipelineConfigurator<I, O> pipelineConfigurator,
                                                         ConnectionHandler<I, O> connectionHandler) {
        return new UdpServerBuilder<I, O>(port, connectionHandler).pipelineConfigurator(pipelineConfigurator).build();
    }

    public static <I, O> RxClient<I, O> createUdpClient(String host, int port,
                                                        PipelineConfigurator<O, I> pipelineConfigurator) {
        return new UdpClientBuilder<I, O>(host, port).channel(NioDatagramChannel.class)
                .eventloop(getRxEventLoopProvider().globalClientEventLoop())
                .pipelineConfigurator(pipelineConfigurator)
                .build();
    }

    public static UdpServer<DatagramPacket, DatagramPacket> createUdpServer(final int port,
                                                                            ConnectionHandler<DatagramPacket, DatagramPacket> connectionHandler) {
        return new UdpServerBuilder<DatagramPacket, DatagramPacket>(port, connectionHandler).build();
    }

    public static RxClient<DatagramPacket, DatagramPacket> createUdpClient(String host, int port) {
        return new UdpClientBuilder<DatagramPacket, DatagramPacket>(host, port)
                .channel(NioDatagramChannel.class)
                .eventloop(getRxEventLoopProvider().globalClientEventLoop()).build();
    }

    public static <I, O> RxServer<I, O> createTcpServer(final int port, PipelineConfigurator<I, O> pipelineConfigurator,
                                                        ConnectionHandler<I, O> connectionHandler) {
        return new ServerBuilder<I, O>(port, connectionHandler).pipelineConfigurator(pipelineConfigurator).build();
    }

    public static <I, O> RxClient<I, O> createTcpClient(String host, int port, PipelineConfigurator<O, I> configurator) {
        return new ClientBuilder<I, O>(host, port).pipelineConfigurator(configurator).build();
    }

    public static RxServer<ByteBuf, ByteBuf> createTcpServer(final int port,
                                                             ConnectionHandler<ByteBuf, ByteBuf> connectionHandler) {
        return new ServerBuilder<ByteBuf, ByteBuf>(port, connectionHandler).build();
    }

    public static RxClient<ByteBuf, ByteBuf> createTcpClient(String host, int port) {
        return new ClientBuilder<ByteBuf, ByteBuf>(host, port).build();
    }

    public static HttpServer<ByteBuf, ByteBuf> createHttpServer(int port, RequestHandler<ByteBuf, ByteBuf> requestHandler) {
        return new HttpServerBuilder<ByteBuf, ByteBuf>(port, requestHandler).build();
    }

    public static HttpClient<ByteBuf, ByteBuf> createHttpClient(String host, int port) {
        return new HttpClientBuilder<ByteBuf, ByteBuf>(host, port).withMaxConnections(DEFAULT_MAX_CONNECTIONS).build();
    }

    public static <I, O> HttpServer<I, O> createHttpServer(int port,
                                                           RequestHandler<I, O> requestHandler,
                                                           PipelineConfigurator<HttpServerRequest<I>, HttpServerResponse<O>> configurator) {
        return new HttpServerBuilder<I, O>(port, requestHandler).pipelineConfigurator(configurator).build();
    }

    public static <I, O> HttpClient<I, O> createHttpClient(String host, int port,
                                                           PipelineConfigurator<HttpClientResponse<O>,
                                                                                HttpClientRequest<I>> configurator) {
        return new HttpClientBuilder<I, O>(host, port).pipelineConfigurator(configurator)
                                                      .withMaxConnections(DEFAULT_MAX_CONNECTIONS).build();
    }

    /**
     * An implementation of {@link RxEventLoopProvider} to be used by all clients and servers created after this call.
     *
     * @param provider New provider to use.
     *
     * @return Existing provider.
     */
    public static RxEventLoopProvider useEventLoopProvider(RxEventLoopProvider provider) {
        RxEventLoopProvider oldProvider = rxEventLoopProvider;
        rxEventLoopProvider = provider;
        return oldProvider;
    }

    public static RxEventLoopProvider getRxEventLoopProvider() {
        return rxEventLoopProvider;
    }
}
