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

package reactivefeign.webclient.allfeatures;

import feign.*;
import org.reactivestreams.Publisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

@Headers({ "Accept: application/json" })
public interface AllFeaturesApi {

	@RequestLine("GET /mirrorParameters/{parameterInPathPlaceholder}?paramInUrl={paramInQueryPlaceholder}")
	Mono<Map<String, String>> mirrorParameters(
			@Param("parameterInPathPlaceholder") long paramInPath,
			@Param("paramInQueryPlaceholder") long paramInQuery,
			@QueryMap Map<String, String> paramMap);

	@RequestLine("GET /mirrorParametersNew?paramInUrl={paramInUrlPlaceholder}")
	Mono<Map<String, String>> mirrorParametersNew(
			@Param("paramInUrlPlaceholder") long paramInUrl,
			@Param("dynamicParam") long dynamicParam,
			@QueryMap Map<String, String> paramMap);

	@RequestLine("GET /mirrorHeaders")
	@Headers({ "Method-Header: {headerValue}" })
	Mono<Map<String, String>> mirrorHeaders(@Param("headerValue") long param,
											@HeaderMap Map<String, String> paramMap);

	@RequestLine("POST " + "/mirrorBody")
	Mono<String> mirrorBody(String body);

	@RequestLine("POST " + "/mirrorBodyMap")
	@Headers({ "Content-Type: application/json" })
	Mono<Map<String, String>> mirrorBodyMap(Map<String, String> body);

	@RequestLine("POST " + "/mirrorBodyReactive")
	@Headers({ "Content-Type: application/json" })
	Mono<String> mirrorBodyReactive(Publisher<String> body);

	@RequestLine("POST " + "/mirrorStreamingBinaryBodyReactive")
	@Headers({ "Content-Type: "+APPLICATION_OCTET_STREAM_VALUE })
	Flux<DataBuffer> mirrorStreamingBinaryBodyReactive(Publisher<DataBuffer> body);

	@RequestLine("POST " + "/mirrorResourceReactiveWithZeroCopying")
	@Headers({ "Content-Type: "+APPLICATION_OCTET_STREAM_VALUE })
	Flux<DataBuffer> mirrorResourceReactiveWithZeroCopying(Resource resource);

	@RequestLine("POST " + "/mirrorBodyMapReactive")
	@Headers({ "Content-Type: application/json" })
	Mono<Map<String, String>> mirrorBodyMapReactive(Publisher<Map<String, String>> body);

	@RequestLine("POST " + "/mirrorBodyStream")
	@Headers({ "Content-Type: application/json" })
	Flux<TestObject> mirrorBodyStream(Publisher<TestObject> bodyStream);

	@RequestLine("GET /empty")
	@Headers({ "Method-Header: {headerValue}" })
	Mono<TestObject> empty();

	default Mono<String> mirrorDefaultBody() {
		return mirrorBody("default");
	}

	class TestObject {

		public String payload;

		public TestObject() {
		}

		public TestObject(String payload) {
			this.payload = payload;
		}
	}

}
