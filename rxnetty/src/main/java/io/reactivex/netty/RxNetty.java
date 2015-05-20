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

package io.reactivex.netty;

import io.reactivex.netty.channel.RxEventLoopProvider;
import io.reactivex.netty.channel.SingleNioLoopProvider;

public final class RxNetty {

    private static volatile RxEventLoopProvider rxEventLoopProvider = new SingleNioLoopProvider(1, Runtime.getRuntime().availableProcessors());

    private static volatile boolean usingNativeTransport;

    private RxNetty() {
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

    /**
     * A global flag to start using netty's <a href="https://github.com/netty/netty/wiki/Native-transports">native protocol</a>
     * if applicable for a client or server.
     *
     * <b>This does not evaluate whether the native transport is available for the OS or not.</b>
     *
     * So, this method should be called conditionally when the caller is sure that the OS supports the native protocol.
     *
     * Alternatively, this can be done selectively per client and server instance by doing the following:
     *
     * <h2>Http Server</h2>
     <pre>
     * {@code
       RxNetty.newHttpServerBuilder(8888, new RequestHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<Object> request, HttpServerResponse<Object> response) {
            return null;
            }
       }).channel(EpollServerSocketChannel.class)
         .eventLoop(new EpollEventLoopGroup());
      }
     </pre>
     *
     * <h2>Http Client</h2>
     *
     <pre>
     {@code
     RxNetty.newHttpClientBuilder("localhost", 8888)
            .channel(EpollSocketChannel.class)
            .eventloop(new EpollEventLoopGroup());
     }
     </pre>
     */
    public static void useNativeTransportIfApplicable() {
        usingNativeTransport = true;
    }

    /**
     * A global flag to disable the effects of calling {@link #useNativeTransportIfApplicable()}
     */
    public static void disableNativeTransport() {
        usingNativeTransport = false;
    }

    public static boolean isUsingNativeTransport() {
        return usingNativeTransport;
    }
}
