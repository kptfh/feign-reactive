package reactivefeign.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

public interface ReactiveStatusHandler {

	boolean shouldHandle(HttpStatus status);

	Mono<? extends Throwable> decode(String methodKey, ClientResponse response);
}