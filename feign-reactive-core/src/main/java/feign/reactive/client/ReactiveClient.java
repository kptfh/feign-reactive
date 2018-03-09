package feign.reactive.client;

import org.reactivestreams.Publisher;

/**
 * @author Sergii Karpenko
 */
public interface ReactiveClient {

    Publisher<Object> executeRequest(ReactiveRequest request);
}
