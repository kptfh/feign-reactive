package reactivefeign.client;


import org.reactivestreams.Publisher;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;

import java.net.URI;

import static feign.Util.checkNotNull;
import static java.util.Collections.emptyList;

/**
 * An immutable request to an http server.
 * @author Sergii Karpenko
 */
public final class ReactiveRequest {

    /**
     * No parameters can be null except {@code body}. All parameters must be
     * effectively immutable, via safe copies, not mutating or otherwise.
     */
    public static ReactiveRequest create(HttpMethod method, URI uri, MultiValueMap<String, String> headers,
                                         Publisher<Object> body) {
        return new ReactiveRequest(method, uri, headers, body);
    }

    private final HttpMethod method;
    private final URI uri;
    private final MultiValueMap<String, String> headers;
    private final Publisher<Object> body;

    public ReactiveRequest(HttpMethod method, URI uri, MultiValueMap<String, String> headers, Publisher<Object> body) {
        this.method = checkNotNull(method, "method of %s", uri);
        this.uri = checkNotNull(uri, "url");
        this.headers = checkNotNull(headers, "headers of %s %s", method, uri);
        this.body = body; // nullable
    }

    /* Method to invoke on the server. */
    public HttpMethod method() {
        return method;
    }

    /* Fully resolved URL including query. */
    public URI uri() {
        return uri;
    }

    /* Ordered list of headers that will be sent to the server. */
    public MultiValueMap<String, String> headers() {
        return headers;
    }


    /**
     * If present, this is the replayable body to send to the server.
     */
    public Publisher<Object> body() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(' ').append(uri).append(" HTTP/1.1\n");
        for (String field : headers.keySet()) {
            for (String value : headers.getOrDefault(field, emptyList())) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (body != null) {
            builder.append('\n').append(body);
        }
        return builder.toString();
    }

}