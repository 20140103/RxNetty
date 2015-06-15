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
package io.reactivex.netty.protocol.http.server.events;

import io.reactivex.netty.events.internal.SafeEventListener;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SafeHttpServerEventsListener extends HttpServerEventsListener implements SafeEventListener {

    private final AtomicBoolean completed = new AtomicBoolean();
    private final HttpServerEventsListener delegate;

    public SafeHttpServerEventsListener(HttpServerEventsListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onCompleted() {
        if (completed.compareAndSet(false, true)) {
            delegate.onCompleted();
        }
    }

    @Override
    public void onNewRequestReceived() {
        delegate.onNewRequestReceived();
    }

    @Override
    public void onRequestHandlingStart(long duration, TimeUnit timeUnit) {
        delegate.onRequestHandlingStart(duration, timeUnit);
    }

    @Override
    public void onRequestHandlingSuccess(long duration, TimeUnit timeUnit) {
        delegate.onRequestHandlingSuccess(duration, timeUnit);
    }

    @Override
    public void onRequestHandlingFailed(long duration, TimeUnit timeUnit,
                                        Throwable throwable) {
        delegate.onRequestHandlingFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onRequestHeadersReceived() {
        delegate.onRequestHeadersReceived();
    }

    @Override
    public void onRequestContentReceived() {
        delegate.onRequestContentReceived();
    }

    @Override
    public void onRequestReceiveComplete(long duration, TimeUnit timeUnit) {
        delegate.onRequestReceiveComplete(duration, timeUnit);
    }

    @Override
    public void onResponseWriteStart() {
        delegate.onResponseWriteStart();
    }

    @Override
    public void onResponseWriteSuccess(long duration, TimeUnit timeUnit, int responseCode) {
        delegate.onResponseWriteSuccess(duration, timeUnit, responseCode);
    }

    @Override
    public void onResponseWriteFailed(long duration, TimeUnit timeUnit,
                                      Throwable throwable) {
        delegate.onResponseWriteFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onNewClientConnected() {
        delegate.onNewClientConnected();
    }

    @Override
    public void onConnectionHandlingStart(long duration, TimeUnit timeUnit) {
        delegate.onConnectionHandlingStart(duration, timeUnit);
    }

    @Override
    public void onConnectionHandlingSuccess(long duration, TimeUnit timeUnit) {
        delegate.onConnectionHandlingSuccess(duration, timeUnit);
    }

    @Override
    public void onConnectionHandlingFailed(long duration, TimeUnit timeUnit,
                                           Throwable throwable) {
        delegate.onConnectionHandlingFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onByteRead(long bytesRead) {
        delegate.onByteRead(bytesRead);
    }

    @Override
    public void onFlushStart() {
        delegate.onFlushStart();
    }

    @Override
    public void onFlushSuccess(long duration, TimeUnit timeUnit) {
        delegate.onFlushSuccess(duration, timeUnit);
    }

    @Override
    public void onFlushFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        delegate.onFlushFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onWriteStart() {
        delegate.onWriteStart();
    }

    @Override
    public void onWriteSuccess(long duration, TimeUnit timeUnit, long bytesWritten) {
        delegate.onWriteSuccess(duration, timeUnit, bytesWritten);
    }

    @Override
    public void onWriteFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        delegate.onWriteFailed(duration, timeUnit, throwable);
    }

    @Override
    public void onConnectionCloseStart() {
        delegate.onConnectionCloseStart();
    }

    @Override
    public void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {
        delegate.onConnectionCloseSuccess(duration, timeUnit);
    }

    @Override
    public void onConnectionCloseFailed(long duration, TimeUnit timeUnit,
                                        Throwable throwable) {
        delegate.onConnectionCloseFailed(duration, timeUnit, throwable);
    }
}
