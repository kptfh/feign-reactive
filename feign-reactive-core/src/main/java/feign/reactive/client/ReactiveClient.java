package feign.reactive.client;

import feign.Request;
import org.reactivestreams.Publisher;

/**
 * @author Sergii Karpenko
 */
public interface ReactiveClient {

    Publisher<Object> executeRequest(Request request);
}
