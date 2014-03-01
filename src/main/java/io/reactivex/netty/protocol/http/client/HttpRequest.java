/*
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.protocol.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.netty.serialization.ByteTransformer;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nitesh Kant
 */
public class HttpRequest<T> {
    static class SimpleContentSourceFactory<T> implements ContentSourceFactory<T, ContentSource<T>> {
        private ContentSource<T> source;
        
        SimpleContentSourceFactory(ContentSource<T> source) {
            this.source = source;    
        }
        @Override
        public ContentSource<T> newContentSource() {
            return source;
        }
    }
    
    static class SimpleRawContentSourceFactory<T> implements ContentSourceFactory<T, RawContentSource<T>> {
        private RawContentSource<T> source;
        
        SimpleRawContentSourceFactory(RawContentSource<T> source) {
            this.source = source;    
        }
        @Override
        public RawContentSource<T> newContentSource() {
            return source;
        }
    }

    private final io.netty.handler.codec.http.HttpRequest nettyRequest;
    private final HttpRequestHeaders headers;
    protected ContentSourceFactory<T, ContentSource<T>> contentFactory;
    protected ContentSourceFactory<?, RawContentSource<?>> rawContentFactory;
    protected boolean userPassedInFactory = false;

    private AtomicBoolean contentSet = new AtomicBoolean();

    /*Visible for testing*/ HttpRequest(io.netty.handler.codec.http.HttpRequest nettyRequest) {
        this.nettyRequest = nettyRequest;
        headers = new HttpRequestHeaders(nettyRequest);
        contentFactory = null;
        rawContentFactory = null;
    }

    public static HttpRequest<ByteBuf> createGet(String uri) {
        return create(HttpMethod.GET, uri);
    }

    public static HttpRequest<ByteBuf> createPost(String uri) {
        return create(HttpMethod.POST, uri);
    }

    public static HttpRequest<ByteBuf> createPut(String uri) {
        return create(HttpMethod.PUT, uri);
    }

    public static HttpRequest<ByteBuf> createDelete(String uri) {
        return create(HttpMethod.DELETE, uri);
    }

    public static <T> HttpRequest<T> create(HttpVersion httpVersion, HttpMethod method, String uri) {
        DefaultHttpRequest nettyRequest = new DefaultHttpRequest(httpVersion, method, uri);
        return new HttpRequest<T>(nettyRequest);
    }

    public static <T> HttpRequest<T> create(HttpMethod method, String uri) {
        return create(HttpVersion.HTTP_1_1, method, uri);
    }

    public HttpRequest<T> withHeader(String name, String value) {
        headers.add(name, value);
        return this;
    }

    public HttpRequest<T> withCookie(Cookie cookie) {
        String cookieHeader = ClientCookieEncoder.encode(cookie);
        return withHeader(HttpHeaders.Names.COOKIE, cookieHeader);
    }

    public HttpRequest<T> withContentSource(ContentSource<T> contentSource) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        this.contentFactory = new SimpleContentSourceFactory<T>(contentSource);
        return this;
    }
    
    public HttpRequest<T> withContentFactory(ContentSourceFactory<T, ContentSource<T>> contentFactory) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        userPassedInFactory = true;
        this.contentFactory = contentFactory;
        return this;
    }
    
    public HttpRequest<T> withRawContentFactory(ContentSourceFactory<?, RawContentSource<?>> rawContentFactory) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        userPassedInFactory = true;
        this.rawContentFactory = rawContentFactory;
        return this;
    }

    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <R> HttpRequest<T> withRawContentSource(final RawContentSource<R> rawContentSource) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        this.rawContentFactory = (ContentSourceFactory) new SimpleRawContentSourceFactory<R>(rawContentSource);
        return this;
    }

    public HttpRequest<T> withContent(T content) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        if (!headers.isContentLengthSet()) {
            headers.set(HttpHeaders.Names.TRANSFER_ENCODING, "chunked");
        }
        contentFactory = new SimpleContentSourceFactory<T>(new ContentSource.SingletonSource<T>(content));
        return this;
    }

    public HttpRequest<T> withContent(String content) {
        return withContent(content.getBytes(Charset.defaultCharset()));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public HttpRequest<T> withContent(byte[] content) {
        if (!contentSet.compareAndSet(false, true)) {
            throw new IllegalStateException("Content has already been set");
        }
        headers.set(HttpHeaders.Names.CONTENT_LENGTH, content.length);
        rawContentFactory = (ContentSourceFactory) new SimpleRawContentSourceFactory(new RawContentSource.SingletonRawSource<byte[]>(content, new ByteTransformer()));
        return this;
    }

    public HttpRequestHeaders getHeaders() {
        return headers;
    }

    public HttpVersion getHttpVersion() {
        return nettyRequest.getProtocolVersion();
    }

    public HttpMethod getMethod() {
        return nettyRequest.getMethod();
    }

    public String getUri() {
        return nettyRequest.getUri();
    }

    io.netty.handler.codec.http.HttpRequest getNettyRequest() {
        return nettyRequest;
    }

    boolean hasRawContentSource() {
        return rawContentFactory != null;
    }
    
    ContentSource<T> getContentSource() {
        return contentFactory.newContentSource();
    }
    
    RawContentSource<?> getRawContentSource() {
        return rawContentFactory.newContentSource();
    }
}
