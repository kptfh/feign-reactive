package reactivefeign.client;

import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface ReactiveHttpResponseMapper
        extends Function<ReactiveHttpResponse, Mono<ReactiveHttpResponse>> {
}
