RxNetty Examples
================

The examples provided are meant to demonstrate how to handle different protocols with RxNetty, as well as to
show some good practices for different usage scenarios.

Running
=======

All examples can be run from a command line. Most of them accepts some parameters, but usually the defaults are just
fine. The examples have their accompanying unit test classes, which run all endpoints in single JVM process.
Running a test class is thus yet another and convenient way to run a particular example specially within an IDE.
 
Examples Catalog
================

Protocol | Example / Test | Description
---------|---------|------------
HTTP | [Hello World](src/main/java/io/reactivex/netty/examples/http/helloworld) ([test](src/test/java/io/reactivex/netty/examples/http/helloworld))      | Simple request - reply client/server implementation.
HTTP | [Server Side Events](src/main/java/io/reactivex/netty/examples/http/sse) ([test](src/test/java/io/reactivex/netty/examples/http/sse))             | This examples demonstrates how to implement server side event stream, and how to handle it on the client side.
HTTP | [Log tail](src/main/java/io/reactivex/netty/examples/http/logtail) ([test](src/test/java/io/reactivex/netty/examples/http/logtail))               | A more sophisticated server side event example, with multiple event sources and an intermediary aggregating separate data streams.
HTTP | [Word Counter](src/main/java/io/reactivex/netty/examples/http/wordcounter) ([test](src/test/java/io/reactivex/netty/examples/http/wordcounter))   | A post request example, and how to handle it efficiently.
TCP  | [Echo Server](src/main/java/io/reactivex/netty/examples/tcp/echo) ([test](src/test/java/io/reactivex/netty/examples/tcp/echo))                    | A simple echo client.
TCP  | [TCP Server Side Event Stream](src/main/java/io/reactivex/netty/examples/tcp/event) ([test](src/test/java/io/reactivex/netty/examples/tcp/event)) | TCP server side event stream example, with configurable client side processing delay to demonstrate????
TCP  | [Interval](src/main/java/io/reactivex/netty/examples/tcp/interval) ([test](src/test/java/io/reactivex/netty/examples/tcp/interval))               | A bit more sophisticated event stream example, with explicit subscribe/unsubscribe control mechanism.
UDP  | [Hello World](src/main/java/io/reactivex/netty/examples/udp) ([test](src/test/java/io/reactivex/netty/examples/udp))                              | UDP version of a simple request - reply client/server implementation.
