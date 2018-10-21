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

import org.awaitility.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import reactivefeign.ReactiveFeign;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.ByteBuffer.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static reactivefeign.ReactivityTest.CALLS_NUMBER;
import static reactivefeign.ReactivityTest.timeToCompleteReactively;
import static reactor.core.publisher.Flux.empty;
import static reactor.core.publisher.Mono.fromFuture;
import static reactor.core.publisher.Mono.just;

/**
 * @author Sergii Karpenko
 *
 * Tests ReactiveFeign in conjunction with WebFlux rest controller.
 */

@RunWith(SpringRunner.class)
@SpringBootTest(
		properties = {"spring.main.web-application-type=reactive"},
		classes = {AllFeaturesController.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
abstract public class AllFeaturesTest {

	private AllFeaturesApi client;

	@LocalServerPort
	private int port;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	abstract protected ReactiveFeign.Builder<AllFeaturesApi> builder();

	@Before
	public void setUp() {
		client = builder()
				.decode404()
				.target(AllFeaturesApi.class, "http://localhost:" + port);
	}

	@Test
	public void shouldReturnAllPassedParameters() {
		Map<String, String> paramMap = new HashMap<String, String>() {
			{
				put("paramKey", "paramValue");
			}
		};
		Map<String, String> returned = client.mirrorParameters(555,777, paramMap).block();

		assertThat(returned).containsEntry("paramInPath", "555");
		assertThat(returned).containsEntry("paramInUrl", "777");
		assertThat(returned).containsAllEntriesOf(paramMap);
	}

	@Test
	public void shouldReturnAllPassedParametersNew() {
		Map<String, String> paramMap = new HashMap<String, String>() {
			{
				put("paramKey", "paramValue");
			}
		};
		Map<String, String> returned = client.mirrorParametersNew(777, 888, paramMap)
				.block();

		assertThat(returned).containsEntry("paramInUrl", "777");
		assertThat(returned).containsEntry("dynamicParam", "888");
		assertThat(returned).containsAllEntriesOf(paramMap);
	}

	@Test
	public void shouldReturnAllPassedHeaders() {
		Map<String, String> headersMap = new HashMap<String, String>() {
			{
				put("headerKey1", "headerValue1");
				put("headerKey2", "headerValue2");
			}
		};
		Map<String, String> returned = client.mirrorHeaders(777, headersMap).block();

		assertThat(returned).containsEntry("Method-Header", "777");
		assertThat(returned).containsAllEntriesOf(headersMap);
		assertThat(returned).containsKey("Accept");
	}

	@Test
	public void shouldReturnBody() {
		String returned = client.mirrorBody("Test Body").block();

		assertThat(returned).isEqualTo("Test Body");
	}

	@Test
	public void shouldReturnBodyMap() {
		Map<String, String> bodyMap = new HashMap<String, String>() {
			{
				put("key1", "value1");
				put("key2", "value2");
			}
		};

		Map<String, String> returned = client.mirrorBodyMap(bodyMap).block();
		assertThat(returned).containsAllEntriesOf(bodyMap);
	}

	@Test
	public void shouldReturnBodyReactive() {
		String returned = client.mirrorBodyReactive(just("Test Body")).block();
		assertThat(returned).isEqualTo("Test Body");
	}

	@Test
	public void shouldReturnBodyMapReactive() {
		Map<String, String> bodyMap = new HashMap<String, String>() {
			{
				put("key1", "value1");
				put("key2", "value2");
			}
		};

		Mono<Map<String, String>> publisher = client.mirrorBodyMapReactive(just(bodyMap));
		StepVerifier.create(publisher)
				.consumeNextWith(map -> assertThat(map).containsAllEntriesOf(bodyMap))
				.verifyComplete();
	}

	@Test
	public void shouldReturnFirstResultBeforeSecondSent() throws InterruptedException {

		CountDownLatch countDownLatch = new CountDownLatch(2);

		AtomicInteger sentCount = new AtomicInteger();
		AtomicInteger receivedCount = new AtomicInteger();

		CompletableFuture<AllFeaturesApi.TestObject> firstReceived = new CompletableFuture<>();

		Flux<AllFeaturesApi.TestObject> returned = client
				.mirrorBodyStream(Flux.just(new AllFeaturesApi.TestObject("testMessage1"),
						new AllFeaturesApi.TestObject("testMessage2")))
				.delayUntil(testObject -> sentCount.get() == 1 ? fromFuture(firstReceived)
						: empty())
				.doOnNext(sent -> sentCount.incrementAndGet());

		returned.doOnNext(received -> {
			receivedCount.incrementAndGet();
			assertThat(receivedCount.get()).isEqualTo(sentCount.get());
			firstReceived.complete(received);
			countDownLatch.countDown();
		}).subscribe();

		countDownLatch.await();
	}

	@Test
	public void shouldReturnEmpty() {
		Optional<AllFeaturesApi.TestObject> returned = client.empty().blockOptional();
		assertThat(!returned.isPresent());
	}

	@Test
	public void shouldReturnDefaultBody() {
		String returned = client.mirrorDefaultBody().block();
		assertThat(returned).isEqualTo("default");
	}


	@Test
	public void shouldRunReactively() {

		AtomicInteger counter = new AtomicInteger();

		for (int i = 0; i < CALLS_NUMBER; i++) {
			client.mirrorBodyWithDelay("testBody")
					.doOnNext(order -> counter.incrementAndGet())
					.subscribe();
		}

		waitAtMost(new Duration(timeToCompleteReactively(), TimeUnit.MILLISECONDS))
				.until(() -> counter.get() == CALLS_NUMBER);
	}

	@Test
	public void shouldMirrorStreamingBinaryBodyReactive() throws InterruptedException {

		CountDownLatch countDownLatch = new CountDownLatch(2);

		AtomicInteger sentCount = new AtomicInteger();
		ConcurrentLinkedQueue<ByteBuffer> receivedAll = new ConcurrentLinkedQueue<>();

		CompletableFuture<ByteBuffer> firstReceived = new CompletableFuture<>();

		Flux<ByteBuffer> returned = client
				.mirrorStreamingBinaryBodyReactive(Flux.just(
						fromByteArray(new byte[]{1,2,3}),
						fromByteArray(new byte[]{4,5,6})))
				.delayUntil(testObject -> sentCount.get() == 1 ? fromFuture(firstReceived)
						: empty())
				.doOnNext(sent -> sentCount.incrementAndGet());

		returned.doOnNext(received -> {
			receivedAll.add(received);
			assertThat(receivedAll.size()).isEqualTo(sentCount.get());
			firstReceived.complete(received);
			countDownLatch.countDown();
		}).subscribe();

		countDownLatch.await();

		assertThat(receivedAll.stream().collect(Collectors.toList()))
				.containsExactly(wrap(new byte[]{1,2,3}), wrap(new byte[]{4,5,6}));
	}

	private static ByteBuffer fromByteArray(byte[] data){
		return ByteBuffer.wrap(data);
	}

}
