# RxNetty Releases #


### Version 0.3.8 ###

[Milestone](https://github.com/Netflix/RxNetty/issues?milestone=5&state=closed)

* [Issue 150] (https://github.com/Netflix/RxNetty/issues/150) Removed deprecated PoolStats.
* [Issue 169] (https://github.com/Netflix/RxNetty/issues/169) Removed ContentSource in favor of Observable for HttpClientRequest
* [Issue 172] (https://github.com/Netflix/RxNetty/issues/172) Missing connection pool events from client built by RxContexts constructs
* [Issue 175] (https://github.com/Netflix/RxNetty/issues/175) Optionally disable System time calls for metrics
* [Issue 177] (https://github.com/Netflix/RxNetty/issues/177) For HTTP server use channelReadComplete() to flush writes

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.rxnetty%22%20AND%20v%3A%220.3.8%22)

### Version 0.3.7 ###

[Milestone](https://github.com/Netflix/RxNetty/issues?milestone=4&state=closed)

* [Issue 98] (https://github.com/Netflix/RxNetty/issues/98) Added pluggable metrics infrastructure with rx-netty-servo implementation.
* [Issue 106] (https://github.com/Netflix/RxNetty/issues/106) Added TLS support for TCP & HTTP (HTTPS)
* [Issue 115] (https://github.com/Netflix/RxNetty/issues/115) ByteBuf leak fixed for both HTTP client and server.
* [Issue 141] (https://github.com/Netflix/RxNetty/issues/141) ServerSentEventEncoder modified to match the specifications and the decoder.
* [Issue 158] (https://github.com/Netflix/RxNetty/issues/158) HttpClientResponse and HttpServerRequest modified to take Subject instead of PublishSubject in the constructors.
* [Issue 160] (https://github.com/Netflix/RxNetty/issues/160) ServerRequestResponseConverter was using the same content subject for all requests on a channel.
* [Issue 164] (https://github.com/Netflix/RxNetty/issues/164) Removed flatmap() usage from HttpConnectionHandler for performance reasons.
* [Issue 166] (https://github.com/Netflix/RxNetty/issues/166) RxServer modified to optionally use a separate acceptor eventloop group.
* [Issue 167] (https://github.com/Netflix/RxNetty/issues/167) Not sending "Connection: keep-alive" header for HTTP 1.1 requests.

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.rxnetty%22%20AND%20v%3A%220.3.7%22)

### Version 0.3.6 ###

[Milestone](https://github.com/Netflix/RxNetty/issues?milestone=2&state=closed)

* [Issue 111] (https://github.com/Netflix/RxNetty/issues/111) Unsubscribing an SSE stream does not close the underlying connection
* [Issue 126] (https://github.com/Netflix/RxNetty/issues/126) HttpServer not compliant to HTTP 1.0 requests
* [Issue 127] (https://github.com/Netflix/RxNetty/issues/127) Revamp the examples.
* [Issue 129] (https://github.com/Netflix/RxNetty/issues/129) HTTP Redirect fails when there is a connection pool
* [Issue 130] (https://github.com/Netflix/RxNetty/issues/130) HTTP keep-alive connections not reusable with chunked transfer encoding
* [Issue 131] (https://github.com/Netflix/RxNetty/issues/131) HTTP redirect loop & max redirect detection is broken
* [Issue 134] (https://github.com/Netflix/RxNetty/issues/134) Provide default http header name for request ID in request context
* [Pull 135] (https://github.com/Netflix/RxNetty/issues/135) Added convenience method to add a single object with ContentTransformer to the HttpClientRequest
* [Issue 139] (https://github.com/Netflix/RxNetty/issues/139) HttpServerResponse.close() should be idempotent
* [Issue 143] (https://github.com/Netflix/RxNetty/issues/143) Write FullHttpResponse when possible
* [Issue 145] (https://github.com/Netflix/RxNetty/issues/145) ObservableConnection.cleanupConnection() does a blocking call.

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.rxnetty%22%20AND%20v%3A%220.3.6%22)

### Version 0.3.5 ###

[Milestone](https://github.com/Netflix/RxNetty/issues?milestone=1&page=1&state=closed)

* [Issue 55] (https://github.com/Netflix/RxNetty/issues/55) Add wire debugging functionality
* [Issue 88] (https://github.com/Netflix/RxNetty/issues/88) Move host and port to RxClient method signature
* [Issue 90] (https://github.com/Netflix/RxNetty/issues/90) Server onError Handling
* [Issue 101] (https://github.com/Netflix/RxNetty/issues/101) Infrastructure Contexts
* [Issue 105] (https://github.com/Netflix/RxNetty/issues/105) AbstractClientBuilder.SHARED_IDLE_CLEANUP_SCHEDULER prevents application from closing.
* [Pull 108] (https://github.com/Netflix/RxNetty/issues/108) UDP support.
* [Issue 113] (https://github.com/Netflix/RxNetty/issues/113) Implement Redirect as an Rx Operator.
* [Issue 114] (https://github.com/Netflix/RxNetty/issues/114) Remove numerus dependency from rxnetty-core.
* [Pull 116] (https://github.com/Netflix/RxNetty/issues/116) Added toString() to ServerInfo for better inspection.
* [Issue 117] (https://github.com/Netflix/RxNetty/issues/117) RxClientImpl mutates netty's bootstrap per connection
* [Issue 119] (https://github.com/Netflix/RxNetty/issues/119) "Transfer-encoding: chunked" not set for HTTP POST request.

Artifacts: [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.rxnetty%22%20AND%20v%3A%220.3.5%22)

### Version 0.3.4 ###

Skipped release due to manual tag creation.
