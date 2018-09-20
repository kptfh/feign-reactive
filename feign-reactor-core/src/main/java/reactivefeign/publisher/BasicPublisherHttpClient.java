package reactivefeign.publisher;


import org.reactivestreams.Publisher;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.client.ReactiveHttpResponse;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;

/**
 * Wraps {@link PublisherHttpClient}
 *
 * @author Sergii Karpenko
 */
public class BasicPublisherHttpClient implements PublisherHttpClient {

	private final ReactiveHttpClient reactiveHttpClient;

	public static reactivefeign.publisher.PublisherHttpClient toPublisher(ReactiveHttpClient reactiveHttpClient){
		return new BasicPublisherHttpClient(reactiveHttpClient);
	}

	private BasicPublisherHttpClient(ReactiveHttpClient reactiveHttpClient) {
		this.reactiveHttpClient = reactiveHttpClient;
	}

	@Override
	public Publisher<Object> executeRequest(ReactiveHttpRequest request, Type publisherType) {
		Mono<ReactiveHttpResponse> response = reactiveHttpClient.executeRequest(request);
		if (publisherType == Mono.class) {
			return response.flatMap(resp -> (Mono<Object>) resp.body());
		} else {
			return response.flatMapMany(ReactiveHttpResponse::body);
		}
	}
}
