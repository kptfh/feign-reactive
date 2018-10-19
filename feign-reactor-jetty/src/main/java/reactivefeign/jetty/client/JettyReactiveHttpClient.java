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

package reactivefeign.jetty.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.async_.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import feign.MethodMetadata;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.reactive.client.ContentChunk;
import org.eclipse.jetty.reactive.client.ReactiveRequest;
import org.reactivestreams.Publisher;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.client.ReactiveHttpResponse;
import reactivefeign.client.ReadTimeoutException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static feign.Util.resolveLastTypeParameter;
import static java.util.Optional.ofNullable;
import static reactivefeign.utils.FeignUtils.getBodyActualType;

/**
 * Uses reactive Jetty client to execute http requests
 * @author Sergii Karpenko
 */
public class JettyReactiveHttpClient implements ReactiveHttpClient {

	public static final String CONTENT_TYPE_HEADER = "Content-Type";
	private final HttpClient httpClient;
	private final Class bodyActualClass;
	private final Class returnPublisherClass;
	private final Class returnActualClass;
	private final JsonFactory jsonFactory;
	private final ObjectWriter bodyWriter;
	private final ObjectReader responseReader;
	private long requestTimeout = -1;

	public static JettyReactiveHttpClient jettyClient(
			MethodMetadata methodMetadata,
			HttpClient httpClient,
			JsonFactory jsonFactory, ObjectMapper objectMapper) {

		final Type returnType = methodMetadata.returnType();
		Class returnPublisherType = (Class)((ParameterizedType) returnType).getRawType();
		Class returnActualType = getClass(resolveLastTypeParameter(returnType, returnPublisherType));
		Class bodyActualType = getClass(getBodyActualType(methodMetadata.bodyType()));

		return new JettyReactiveHttpClient(httpClient,
				bodyActualType, returnPublisherType, returnActualType,
				jsonFactory,
				objectMapper.writerFor(bodyActualType),
				objectMapper.readerFor(returnActualType));
	}

	public JettyReactiveHttpClient(HttpClient httpClient,
								   Class bodyActualClass, Class returnPublisherClass, Class returnActualClass,
								   JsonFactory jsonFactory, ObjectWriter bodyWriter, ObjectReader responseReader) {
		this.httpClient = httpClient;
		this.bodyActualClass = bodyActualClass;
		this.returnPublisherClass = returnPublisherClass;
		this.returnActualClass = returnActualClass;
		this.jsonFactory = jsonFactory;
		this.bodyWriter = bodyWriter;
		this.responseReader = responseReader;
	}

	public JettyReactiveHttpClient setRequestTimeout(long timeoutInMillis){
		this.requestTimeout = timeoutInMillis;
		return this;
	}

	@Override
	public Mono<ReactiveHttpResponse> executeRequest(ReactiveHttpRequest request) {
		Request jettyRequest = httpClient.newRequest(request.uri()).method(request.method());
		setUpHeaders(request, jettyRequest.getHeaders());
		if(requestTimeout > 0){
			jettyRequest.timeout(requestTimeout, TimeUnit.MILLISECONDS);
		}

		ReactiveRequest.Builder requestBuilder = ReactiveRequest.newBuilder(jettyRequest);
		if(bodyActualClass != null){
			requestBuilder.content(provideBody(request));
		}

		Publisher<JettyReactiveHttpResponse> responsePublisher = requestBuilder.build().response(
				(reactiveResponse, contentChunkPublisher) -> Mono.just(new JettyReactiveHttpResponse(
						reactiveResponse, contentChunkPublisher, returnPublisherClass, returnActualClass,
						jsonFactory, responseReader)));

		return Mono.<ReactiveHttpResponse>from(responsePublisher)
				.onErrorMap(ex -> ex instanceof java.util.concurrent.TimeoutException,
						ReadTimeoutException::new)
           ;
	}

	protected void setUpHeaders(ReactiveHttpRequest request, HttpFields httpHeaders) {
		request.headers().forEach(httpHeaders::put);
	}

	protected ReactiveRequest.Content provideBody(ReactiveHttpRequest request) {
		String contentTypeFromHeader = ofNullable(request.headers().get(CONTENT_TYPE_HEADER))
				.map(strings -> strings.get(0)).orElse(null);

		Publisher<ContentChunk> bodyPublisher;
		String contentType;
		if(bodyActualClass == ByteBuffer.class){
			bodyPublisher = Flux.from(request.body()).map(this::toByteBufferChunk);
			contentType = "application/octet-stream";
		}
		else if(bodyActualClass == byte[].class){
			bodyPublisher = Flux.from(request.body()).map(this::toByteArrayChunk);
			contentType = "application/octet-stream";
		}
		else {
			bodyPublisher = Flux.from(request.body()).map(this::toJsonChunk);
			contentType = MimeTypes.Type.APPLICATION_JSON_UTF_8.asString();
		}

		if(contentTypeFromHeader != null){
			contentType = contentTypeFromHeader;
		}
		return ReactiveRequest.Content.fromPublisher(bodyPublisher, contentType);
	}

	protected ContentChunk toByteBufferChunk(Object data){
		return new ContentChunk((ByteBuffer)data);
	}

	protected ContentChunk toByteArrayChunk(Object data){
		return new ContentChunk(ByteBuffer.wrap((byte[])data));
	}

	protected ContentChunk toJsonChunk(Object data){
		try {
			ByteBuffer buffer = ByteBuffer.wrap(bodyWriter.writeValueAsBytes(data));
			return new ContentChunk(buffer);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Class getClass(Type type){
		return (Class)(type instanceof ParameterizedType
				? ((ParameterizedType) type).getRawType() : type);
	}
}
