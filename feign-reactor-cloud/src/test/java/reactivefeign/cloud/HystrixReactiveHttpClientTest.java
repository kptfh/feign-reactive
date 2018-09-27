package reactivefeign.cloud;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import feign.MethodMetadata;
import feign.Target;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Sergii Karpenko
 */
public class HystrixReactiveHttpClientTest {

    public static final int SLEEP_WINDOW = 100;
    public static final int VOLUME_THRESHOLD = 1;
    public static final String FALLBACK = "fallback";
    @ClassRule
    public static WireMockClassRule server = new WireMockClassRule(wireMockConfig().dynamicPort());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static int testNo = 0;

    @Before
    public void resetServers() {
        server.resetAll();

        testNo++;
    }

    @Test
    public void shouldFailAsNoFallback() {

        expectedException.expect(HystrixRuntimeException.class);
        expectedException.expectMessage(containsString("failed and no fallback available"));

        String body = "success!";
        LoadBalancingReactiveHttpClientTest.mockSuccessAfterSeveralAttempts(server, "/", 1, 598,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body));

        LoadBalancingReactiveHttpClientTest.TestInterface client = CloudReactiveFeign.<LoadBalancingReactiveHttpClientTest.TestInterface>builder()
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .target(LoadBalancingReactiveHttpClientTest.TestInterface.class, "http://localhost:" + server.port());

        client.get().block();
    }

    @Test
    public void shouldNotFailDueToFallback() {

        String body = "success!";
        LoadBalancingReactiveHttpClientTest.mockSuccessAfterSeveralAttempts(server, "/", 1, 598,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body));

        LoadBalancingReactiveHttpClientTest.TestInterface client = CloudReactiveFeign.<LoadBalancingReactiveHttpClientTest.TestInterface>builder()
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .setFallback(() -> Mono.just(FALLBACK))
                .target(LoadBalancingReactiveHttpClientTest.TestInterface.class, "http://localhost:" + server.port());

        String result = client.get().block();
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    public void shouldOpenCircuitBreakerAndCloseAfterSleepTime() throws InterruptedException {

        int callsNo = VOLUME_THRESHOLD + 1;
        LoadBalancingReactiveHttpClientTest.mockSuccessAfterSeveralAttempts(server, "/", callsNo, SC_SERVICE_UNAVAILABLE,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("success!"));

        LoadBalancingReactiveHttpClientTest.TestInterface client = CloudReactiveFeign.<LoadBalancingReactiveHttpClientTest.TestInterface>builder()
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .target(LoadBalancingReactiveHttpClientTest.TestInterface.class, "http://localhost:" + server.port());

        //check that circuit breaker get opened on volume threshold
        List<Object> results = IntStream.range(0, callsNo).mapToObj(i -> {
            try {
                return client.get().block();
            } catch (Throwable t) {
                return t;
            }
        }).collect(Collectors.toList());

        assertThat(server.getAllServeEvents().size()).isLessThan(callsNo);
        Throwable firstError = (Throwable) results.get(0);
        assertThat(firstError).isInstanceOf(HystrixRuntimeException.class);
        assertThat(firstError.getMessage()).contains("and no fallback available");

        Throwable lastError = (Throwable) results.get(results.size() - 1);
        assertThat(lastError).isInstanceOf(HystrixRuntimeException.class);
        assertThat(lastError.getMessage()).contains("short-circuited and no fallback available.");

        //wait to circuit breaker get closed again
        Thread.sleep(SLEEP_WINDOW);

        //check that circuit breaker get closed again
        List<Object> resultsAfterSleep = IntStream.range(0, callsNo).mapToObj(i -> {
            try {
                return client.get().block();
            } catch (Throwable t) {
                return t;
            }
        }).collect(Collectors.toList());

        Throwable firstErrorAfterSleep = (Throwable) resultsAfterSleep.get(0);
        assertThat(firstErrorAfterSleep).isInstanceOf(HystrixRuntimeException.class);
        assertThat(firstErrorAfterSleep.getMessage()).contains("and no fallback available");

    }

    static CloudReactiveFeign.SetterFactory getSetterFactory(int testNo) {
        return new CloudReactiveFeign.SetterFactory() {
            @Override
            public HystrixObservableCommand.Setter create(Target<?> target, MethodMetadata methodMetadata) {
                String groupKey = target.name();
                String commandKey = methodMetadata.configKey();
                return HystrixObservableCommand.Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey + testNo))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                                .withCircuitBreakerRequestVolumeThreshold(VOLUME_THRESHOLD)
                                .withExecutionTimeoutEnabled(false)
                                .withCircuitBreakerSleepWindowInMilliseconds(SLEEP_WINDOW)
                        );
            }
        };
    }

}
