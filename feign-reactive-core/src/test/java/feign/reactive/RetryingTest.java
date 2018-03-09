package feign.reactive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import feign.FeignException;
import feign.ReactiveFeign;
import feign.jackson.JacksonEncoder;
import feign.reactive.testcase.IcecreamServiceApi;
import feign.reactive.testcase.domain.IceCreamOrder;
import feign.reactive.testcase.domain.OrderGenerator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Sergii Karpenko
 */
public class RetryingTest {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(wireMockConfig().dynamicPort());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void resetServers() {
        wireMockRule.resetAll();
    }

    @Test
    public void shouldSuccessOnRetries() throws JsonProcessingException {

        IceCreamOrder orderGenerated = new OrderGenerator().generate(1);
        String orderStr = TestUtils.MAPPER.writeValueAsString(orderGenerated);

        mockResponseAfterSeveralAttempts(wireMockRule, 2, "testRetrying_success", "/icecream/orders/1",
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(orderStr));

        IcecreamServiceApi client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(WebClient.create())
                .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

        IceCreamOrder order = client.findOrder(1)
                .retryWhen(ReactiveRetryers.retryWithDelay(3, 0))
                .block();

        assertThat(order)
                .isEqualToComparingFieldByFieldRecursively(orderGenerated);
    }

    private static void mockResponseAfterSeveralAttempts(WireMockClassRule rule, int failedAttemptsNo, String scenario, String url,
                                                         ResponseDefinitionBuilder response) {
        String state = STARTED;
        for (int attempt = 0; attempt < failedAttemptsNo; attempt++) {
            String nextState = "attempt" + attempt;
            rule.stubFor(get(urlEqualTo(url))
                    .withHeader("Accept", equalTo("application/json"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withHeader("Retry-After", "1"))
                    .willSetStateTo(nextState));

            state = nextState;
        }

        rule.stubFor(get(urlEqualTo(url))
                .withHeader("Accept", equalTo("application/json"))
                .inScenario(scenario)
                .whenScenarioStateIs(state)
                .willReturn(response));
    }

    @Test
    public void shouldFailAsNoMoreRetries() {

        expectedException.expect(FeignException.class);
        expectedException.expectMessage(containsString("status 503"));

        String orderUrl = "/icecream/orders/1";

        wireMockRule.stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "1")));

        IcecreamServiceApi client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(WebClient.create())
                .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

        client.findOrder(1)
                .retryWhen(ReactiveRetryers.retryWithDelay(3, 0))
                .block();
    }

}
