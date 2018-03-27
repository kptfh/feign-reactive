package reactivefeign;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.netflix.client.ClientException;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import reactivefeign.CloudReactiveFeign;
import feign.RequestLine;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.netflix.client.ClientFactory.getNamedLoadBalancer;
import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isA;

/**
 * @author Sergii Karpenko
 */
public class LoadBalancingReactiveHttpClientTest {

    @ClassRule
    public static WireMockClassRule server1 = new WireMockClassRule(wireMockConfig().dynamicPort());
    @ClassRule
    public static WireMockClassRule server2 = new WireMockClassRule(wireMockConfig().dynamicPort());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static String serviceName = "LoadBalancingTargetTest-loadBalancingDefaultPolicyRoundRobin";

    @Before
    public void resetServers() {
        server1.resetAll();
        server2.resetAll();
    }

    @Test
    public void shouldLoadBalanceRequests() throws IOException, InterruptedException {
        String body = "success!";
        mockSuccess(server1, body);
        mockSuccess(server2, body);

        String serverListKey = serviceName + ".ribbon.listOfServers";
        getConfigInstance().setProperty(serverListKey, "localhost:" + server1.port() + "," + "localhost:" + server2.port());

        TestInterface client = CloudReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .setLoadBalancerCommand(
                        LoadBalancerCommand.builder()
                                .withLoadBalancer(AbstractLoadBalancer.class.cast(getNamedLoadBalancer(serviceName)))
                                .build()
                )
                .target(TestInterface.class, "http://" + serviceName);

        try {

            String result1 = client.get().block();
            String result2 = client.get().block();

            assertThat(result1)
                    .isEqualTo(result2)
                    .isEqualTo(body);

            server1.verify(1, getRequestedFor(urlEqualTo("/")));
            server2.verify(1, getRequestedFor(urlEqualTo("/")));
        } finally {
            getConfigInstance().clearProperty(serverListKey);
        }
    }

    @Test
    public void shouldFailAsPolicyWoRetries() throws IOException, InterruptedException {

        expectedException.expect(feign.RetryableException.class);

        try {
            loadBalancingWithRetry(2, 0, 0);
        } catch (Throwable t) {
            assertThat(server1.getAllServeEvents().size() == 1
                    ^ server2.getAllServeEvents().size() == 1);
            throw t;
        }
    }

    @Test
    public void shouldRetryOnSameAndFail() throws IOException, InterruptedException {

        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(isA(ClientException.class));

        try {
            loadBalancingWithRetry(2, 1, 0);
        } catch (Throwable t) {
            assertThat(server1.getAllServeEvents().size() == 2
                    ^ server2.getAllServeEvents().size() == 2);
            throw t;
        }
    }

    @Test
    public void shouldRetryOnNextAndFail() throws IOException, InterruptedException {

        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(isA(ClientException.class));

        try {
            loadBalancingWithRetry(2, 1, 1);
        } catch (Throwable t) {
            assertThat(server1.getAllServeEvents().size() == 2
                    && server2.getAllServeEvents().size() == 2);
            throw t;
        }
    }

    @Test
    public void shouldRetryOnSameAndSuccess() throws IOException, InterruptedException {

        loadBalancingWithRetry(2, 2, 0);

        assertThat(server1.getAllServeEvents().size() == 3
                ^ server2.getAllServeEvents().size() == 3);

    }

    private void loadBalancingWithRetry(int failedAttemptsNo, int retryOnSame, int retryOnNext) throws IOException, InterruptedException {
        String body = "success!";
        Stream.of(server1, server2).forEach(server -> {
            mockSuccessAfterSeveralAttempts(server, "/",
                    failedAttemptsNo, 503,
                    aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(body));
        });

        String serverListKey = serviceName + ".ribbon.listOfServers";
        getConfigInstance().setProperty(serverListKey, "localhost:" + server1.port() + "," + "localhost:" + server2.port());

        RetryHandler retryHandler = new RequestSpecificRetryHandler(true, true,
                new DefaultLoadBalancerRetryHandler(retryOnSame, retryOnNext, true), null);

        TestInterface client = CloudReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .setLoadBalancerCommand(
                        LoadBalancerCommand.builder()
                                .withLoadBalancer(AbstractLoadBalancer.class.cast(getNamedLoadBalancer(serviceName)))
                                .withRetryHandler(retryHandler)
                                .build()
                )
                .target(TestInterface.class, "http://" + serviceName);

        try {

            String result = client.get().block();
            assertThat(result).isEqualTo(body);
        } finally {
            getConfigInstance().clearProperty(serverListKey);
        }
    }

    static void mockSuccess(WireMockClassRule server, String body) {
        server.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    static void mockSuccessAfterSeveralAttempts(WireMockClassRule server, String url,
                                                int failedAttemptsNo, int errorCode, ResponseDefinitionBuilder response) {
        String state = STARTED;
        for (int attempt = 0; attempt < failedAttemptsNo; attempt++) {
            String nextState = "attempt" + attempt;
            server.stubFor(get(urlEqualTo(url))
                    .inScenario("testScenario")
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withStatus(errorCode)
                            .withHeader("Retry-After", "1"))
                    .willSetStateTo(nextState));

            state = nextState;
        }

        server.stubFor(get(urlEqualTo(url))
                .inScenario("testScenario")
                .whenScenarioStateIs(state)
                .willReturn(response));
    }


    interface TestInterface {

        @RequestLine("GET /")
        Mono<String> get();
    }
}
