package io.reactivex.netty.protocol.http.websocket;

import java.net.URI;
import java.net.URISyntaxException;

import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.AbstractClientBuilder;
import io.reactivex.netty.client.ClientChannelFactory;
import io.reactivex.netty.client.ClientChannelFactoryImpl;
import io.reactivex.netty.client.ClientConnectionFactory;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.UnpooledClientConnectionFactory;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.pipeline.PipelineConfigurator;

/**
 * @author Tomasz Bak
 */
@SuppressWarnings("unchecked")
public class WebSocketClientBuilder<T extends WebSocketFrame> extends AbstractClientBuilder<T, T, WebSocketClientBuilder<T>, WebSocketClient<T>> {

    private URI webSocketURI = URI.create("/");
    private WebSocketVersion webSocketVersion = WebSocketVersion.V13;
    private boolean messageAggregation;
    private String subprotocol;
    private boolean allowExtensions;
    private int maxFramePayloadLength = 65536;


    public WebSocketClientBuilder(String host, int port) {
        this(host, port, new Bootstrap());
    }

    public WebSocketClientBuilder(String host, int port, Bootstrap bootstrap) {
        this(bootstrap, host, port, new UnpooledClientConnectionFactory<T, T>(),
                new ClientChannelFactoryImpl<T, T>(bootstrap));
    }

    public WebSocketClientBuilder(Bootstrap bootstrap, String host, int port,
                                  ClientConnectionFactory<T, T, ObservableConnection<T, T>> connectionFactory,
                                  ClientChannelFactory<T, T> factory) {
        super(bootstrap, host, port, connectionFactory, factory);
    }

    @Override
    protected WebSocketClient<T> createClient() {
        PipelineConfigurator<T, T> webSocketPipeline = new WebSocketClientPipelineConfigurator<T, T>(
                webSocketURI, webSocketVersion, subprotocol, allowExtensions, maxFramePayloadLength, messageAggregation);
        if (getPipelineConfigurator() != null) {
            appendPipelineConfigurator(webSocketPipeline);
        } else {
            pipelineConfigurator(webSocketPipeline);
        }
        return new WebSocketClient<T>(getOrCreateName(), serverInfo, bootstrap, pipelineConfigurator, clientConfig,
                channelFactory, connectionFactory, eventsSubject);
    }

    @Override
    protected String generatedNamePrefix() {
        return "WebSocketClient-";
    }

    public WebSocketClientBuilder<T> withWebSocketURI(String uri) {
        try {
            webSocketURI = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    public WebSocketClientBuilder<T> withWebSocketVersion(WebSocketVersion version) {
        webSocketVersion = version;
        return this;
    }

    public WebSocketClientBuilder<T> withMessageAggregator(boolean messageAggregation) {
        this.messageAggregation = messageAggregation;
        return this;
    }

    public WebSocketClientBuilder<T> withSubprotocol(String subprotocol) {
        this.subprotocol = subprotocol;
        return this;
    }

    public WebSocketClientBuilder<T> withAllowExtensions(boolean allowExtensions) {
        this.allowExtensions = allowExtensions;
        return this;
    }

    public WebSocketClientBuilder<T> withMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
        return this;
    }

    @Override
    protected MetricEventsListener<? extends ClientMetricsEvent<? extends Enum>> newMetricsListener(MetricEventsListenerFactory factory, WebSocketClient<T> client) {
        return null;
    }
}
