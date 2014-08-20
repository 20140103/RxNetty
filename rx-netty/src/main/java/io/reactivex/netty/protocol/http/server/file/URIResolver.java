package io.reactivex.netty.protocol.http.server.file;

import java.net.URI;

/**
 * Plugable policy to resolve a request URI to a local URL
 * 
 * @author elandau
 */
public interface URIResolver {
    /**
     * Resolve the path URI to a local URL 
     * 
     * @param uri
     * @return A local URL or null if not found
     */
    public URI getUri(String uri);
}
