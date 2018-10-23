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

package reactivefeign.allfeatures;

import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_STREAM_JSON_VALUE;
import static reactor.core.publisher.Mono.just;

@RestController
public class AllFeaturesController implements AllFeaturesApi {

	@GetMapping(path = "/mirrorParameters/{paramInPath}")
	@Override
	public Mono<Map<String, String>> mirrorParameters(
			@PathVariable("paramInPath") long paramInPath,
			@RequestParam("paramInUrl") long paramInUrl,
			@RequestParam Map<String, String> paramMap) {
		paramMap.put("paramInPath", Long.toString(paramInPath));
		paramMap.put("paramInUrl", Long.toString(paramInUrl));
		return just(paramMap);
	}

	@GetMapping(path = "/mirrorParametersNew")
	@Override
	public Mono<Map<String, String>> mirrorParametersNew(
			@RequestParam("paramInUrl") long paramInUrl,
			@RequestParam("dynamicParam") long dynamicParam,
			@RequestParam Map<String, String> paramMap) {
		paramMap.put("paramInUrl", Long.toString(paramInUrl));
		paramMap.put("dynamicParam", Long.toString(dynamicParam));
		return just(paramMap);
	}

	@GetMapping(path = "/mirrorHeaders")
	@Override
	public Mono<Map<String, String>> mirrorHeaders(
			@RequestHeader("Method-Header") long param,
			@RequestHeader Map<String, String> headersMap) {
		return just(headersMap);
	}

	@PostMapping(path = "/mirrorBody")
	@Override
	public Mono<String> mirrorBody(@RequestBody String body) {
		return just(body);
	}

	@PostMapping(path = "/mirrorBodyMap")
	@Override
	public Mono<Map<String, String>> mirrorBodyMap(
			@RequestBody Map<String, String> body) {
		return just(body);
	}

	@PostMapping(path = "/mirrorBodyReactive")
	@Override
	public Mono<String> mirrorBodyReactive(@RequestBody Publisher<String> body) {
		return Mono.from(body);
	}

	@PostMapping(path = "/mirrorBodyMapReactive")
	@Override
	public Mono<Map<String, String>> mirrorBodyMapReactive(
			@RequestBody Publisher<Map<String, String>> body) {
		return Mono.from(body);
	}

	@PostMapping(path = "/mirrorBodyStream")
	@Override
	public Flux<TestObject> mirrorBodyStream(
			@RequestBody Publisher<TestObject> bodyStream) {
		return Flux.from(bodyStream);
	}

	@PostMapping(path = "/mirrorIntegerBodyStream")
	@Override
	public Flux<Integer> mirrorIntegerBodyStream(
			@RequestBody Flux<Integer> body){
		return body;
	}

	@PostMapping(path = "/mirrorStringBodyStream")
	@Override
	public Flux<String> mirrorStringBodyStream(
			@RequestBody  Flux<String> body){
		return body;
	}

	@PostMapping(path = "/mirrorBodyWithDelay")
	@Override
	public Mono<String> mirrorBodyWithDelay(@RequestBody String body) {
		return just(body).delayElement(Duration.ofMillis(500));
	}

	@Override
	public Mono<TestObject> empty() {
		return Mono.empty();
	}

	@PostMapping(path = "/mirrorStreamingBinaryBodyReactive")
	@Override
	public Flux<ByteBuffer> mirrorStreamingBinaryBodyReactive(@RequestBody Publisher<ByteBuffer> body) {
		return Flux.from(body);
	}

}
