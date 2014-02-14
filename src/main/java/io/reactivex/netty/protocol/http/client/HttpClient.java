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
package io.reactivex.netty.protocol.http.client;

import io.reactivex.netty.client.RxClient;
import rx.Observable;

/**
 *
 * @param <I> The type of the content of request.
 * @param <O> The type of the content of response.
 */
public interface HttpClient<I, O> extends RxClient<HttpRequest<I>, HttpResponse<O>>{

    Observable<HttpResponse<O>> submit(HttpRequest<I> request);

    Observable<HttpResponse<O>> submit(HttpRequest<I> request, ClientConfig config);

    /**
     * A configuration to be used for this client.
     */
    class HttpClientConfig extends ClientConfig {

        public static final HttpClientConfig DEFAULT_CONFIG = new HttpClientConfig();

        private String userAgent = "RxNetty Client";

        private HttpClientConfig() {
            // Only the builder can create this instance, so that we can change the constructor signature at will.
        }

        public String getUserAgent() {
            return userAgent;
        }

        public static class Builder extends AbstractBuilder<Builder, HttpClientConfig> {

            public Builder(HttpClientConfig defaultConfig) {
                super(null == defaultConfig ? new HttpClientConfig() : defaultConfig);
            }

            public Builder userAgent(String userAgent) {
                config.userAgent = userAgent;
                return returnBuilder();
            }
        }
    }
}
