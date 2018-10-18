/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactivefeign.webclient.client;

import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.client.ReactiveHttpResponse;
import reactivefeign.client.ReadTimeoutException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static feign.Util.resolveLastTypeParameter;
import static java.util.Optional.ofNullable;

/**
 * Uses {@link WebClient} to execute http requests
 * @author Sergii Karpenko
 */
public class WebReactiveHttpClient implements ReactiveHttpClient {

	private final WebClient webClient;
	private final ParameterizedTypeReference<Object> bodyActualType;
	private final Type returnPublisherType;
	private final ParameterizedTypeReference<Object> returnActualType;

	public static WebReactiveHttpClient webClient(MethodMetadata methodMetadata, WebClient webClient) {

		final Type returnType = methodMetadata.returnType();
		Type returnPublisherType = ((ParameterizedType) returnType).getRawType();
		ParameterizedTypeReference<Object> returnActualType = ParameterizedTypeReference.forType(
				resolveLastTypeParameter(returnType, (Class<?>) returnPublisherType));

		return new WebReactiveHttpClient(webClient,
				getBodyActualType(methodMetadata.bodyType()),
				returnPublisherType, returnActualType);
	}

	public WebReactiveHttpClient(WebClient webClient,
								 ParameterizedTypeReference<Object> bodyActualType,
								 Type returnPublisherType, ParameterizedTypeReference<Object> returnActualType) {
		this.webClient = webClient;
		this.bodyActualType = bodyActualType;
		this.returnPublisherType = returnPublisherType;
		this.returnActualType = returnActualType;
	}

	@Override
	public Mono<ReactiveHttpResponse> executeRequest(ReactiveHttpRequest request) {
		return webClient.method(HttpMethod.valueOf(request.method()))
				.uri(request.uri())
				.headers(httpHeaders -> setUpHeaders(request, httpHeaders))
				.body(provideBody(request))
				.exchange()
				.onErrorMap(ex -> ex instanceof io.netty.handler.timeout.ReadTimeoutException,
						ReadTimeoutException::new)
				.doOnError(throwable -> {
					int debug = 0;
				})
				.doOnCancel(() -> {
					int debug = 0;
				})
				.map(response -> new WebReactiveHttpResponse(response, returnPublisherType, returnActualType));
	}

	protected BodyInserter<?, ? super ClientHttpRequest> provideBody(ReactiveHttpRequest request) {
		return bodyActualType != null
                ? BodyInserters.fromPublisher(request.body(), bodyActualType)
                : BodyInserters.empty();
	}

	protected void setUpHeaders(ReactiveHttpRequest request, HttpHeaders httpHeaders) {
		request.headers().forEach(httpHeaders::put);
	}

	public static ParameterizedTypeReference<Object> getBodyActualType(Type bodyType) {
		return ofNullable(bodyType).map(type -> {
			if (type instanceof ParameterizedType) {
				Class<?> returnBodyType = (Class<?>) ((ParameterizedType) type)
						.getRawType();
				if ((returnBodyType).isAssignableFrom(Publisher.class)) {
					return ParameterizedTypeReference
							.forType(resolveLastTypeParameter(bodyType, returnBodyType));
				}
				else {
					return ParameterizedTypeReference.forType(type);
				}
			}
			else {
				return ParameterizedTypeReference.forType(type);
			}
		}).orElse(null);
	}

}
