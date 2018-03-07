package feign.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import feign.CloudReactiveFeign;
import feign.MethodMetadata;
import feign.Target;
import feign.jackson.JacksonEncoder;
import feign.reactive.LoadBalancingReactiveHttpClientTest.TestInterface;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static feign.reactive.LoadBalancingReactiveHttpClientTest.mockSuccessAfterSeveralAttempts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Sergii Karpenko
 */
public class HystrixReactiveHttpClientTest {

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
    public void shouldFailAsNoFallback() throws IOException, InterruptedException {

        expectedException.expect(HystrixRuntimeException.class);
        expectedException.expectMessage(containsString("failed and no fallback available"));

        String body = "success!";
        mockSuccessAfterSeveralAttempts(server, "/", 1, 598,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body));

        TestInterface client = CloudReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .target(TestInterface.class, "http://localhost:" + server.port());

        client.get().block();
    }

    @Test
    public void shouldNotFailDueToFallback() throws IOException, InterruptedException {

        String body = "success!";
        mockSuccessAfterSeveralAttempts(server, "/", 1, 598,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body));

        TestInterface client = CloudReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .setFallback(new TestInterface() {
                    @Override
                    public Mono<String> get() {
                        return Mono.just("fallback");
                    }
                })
                .target(TestInterface.class, "http://localhost:" + server.port());

        String result = client.get().block();
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    public void shouldOpenCircuitBreaker() throws IOException, InterruptedException {

        String body = "success!";
        int callsNo = 10;
        mockSuccessAfterSeveralAttempts(server, "/", callsNo, 598,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body));

        TestInterface client = CloudReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .setHystrixCommandSetterFactory(getSetterFactory(testNo))
                .target(TestInterface.class, "http://localhost:" + server.port());

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
        assertThat(firstError.getMessage()).contains("failed and no fallback available");

        Throwable lastError = (Throwable) results.get(results.size() - 1);
        assertThat(lastError).isInstanceOf(HystrixRuntimeException.class);
        assertThat(lastError.getMessage()).contains("short-circuited and no fallback available.");

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
                                .withCircuitBreakerRequestVolumeThreshold(1));
            }
        };
    }

}
