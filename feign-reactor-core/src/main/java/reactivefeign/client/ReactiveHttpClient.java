package reactivefeign.client;

import reactor.core.publisher.Mono;

public interface ReactiveHttpClient {

	Mono<ReactiveHttpResponse> executeRequest(ReactiveHttpRequest request);
}
