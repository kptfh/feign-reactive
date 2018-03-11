package feign.reactive.allfeatures;

import feign.ReactiveFeign;
import feign.reactive.allfeatures.AllFeaturesApi.TestObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.empty;
import static reactor.core.publisher.Mono.fromFuture;
import static reactor.core.publisher.Mono.just;

/**
 * @author Sergii Karpenko
 */

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = {AllFeaturesController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class AllFeaturesTest {

    private AllFeaturesApi client;

    @LocalServerPort
    private int port;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void setUp() {
        client = ReactiveFeign.<AllFeaturesApi>builder()
                .webClient(WebClient.create())
                .target(AllFeaturesApi.class, "http://localhost:" + port);
    }

    @Test
    public void shouldReturnAllPassedParameters() {
        Map<String, String> paramMap = new HashMap<String, String>(){
            {put("paramKey", "paramValue");}
        };
        Map<String, String> returned = client.mirrorParameters(777, paramMap).block();

        assertThat(returned).containsEntry("paramInUrl", "777");
        assertThat(returned).containsAllEntriesOf(paramMap);
    }

    @Test
    public void shouldReturnAllPassedParametersNew() {
        Map<String, String> paramMap = new HashMap<String, String>(){
            {put("paramKey", "paramValue");}
        };
        Map<String, String> returned = client.mirrorParametersNew(777, 888, paramMap).block();

        assertThat(returned).containsEntry("paramInUrl", "777");
        assertThat(returned).containsEntry("param", "888");
        assertThat(returned).containsAllEntriesOf(paramMap);
    }

    @Test
    public void shouldReturnAllPassedHeaders() {
        Map<String, String> headersMap = new HashMap<String, String>(){
            {put("headerKey1", "headerValue1");
                put("headerKey2", "headerValue2");}
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
        Map<String, String> bodyMap = new HashMap<String, String>(){
            {put("key1", "value1");
                put("key2", "value2");}
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
        Map<String, String> bodyMap = new HashMap<String, String>(){
            {put("key1", "value1");
                put("key2", "value2");}
        };

        Map<String, String> returned = client.mirrorBodyMapReactive(just(bodyMap)).block();
        assertThat(returned).containsAllEntriesOf(bodyMap);
    }


    @Test
    public void shouldReturnFirstResultBeforeSecondSent() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(2);

        AtomicInteger sentCount = new AtomicInteger();
        AtomicInteger receivedCount = new AtomicInteger();

        CompletableFuture<TestObject> firstReceived = new CompletableFuture<>();

        Flux<TestObject> returned = client.mirrorBodyStream(
                Flux.just(new TestObject("testMessage1"),
                          new TestObject("testMessage2")))
                .delayUntil(testObject -> sentCount.get() == 1
                        ? fromFuture(firstReceived) : empty())
                .doOnNext(sent -> sentCount.incrementAndGet());

        returned.doOnNext(received -> {
            receivedCount.incrementAndGet();
            assertThat(receivedCount.get()).isEqualTo(sentCount.get());
            firstReceived.complete(received);
            countDownLatch.countDown();
        }).subscribe();

        countDownLatch.await();
    }

}
