package feign.reactive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import feign.FeignException;
import feign.Logger;
import feign.ReactiveFeign;
import feign.jackson.JacksonEncoder;
import feign.reactive.testcase.IcecreamServiceApi;
import feign.reactive.testcase.domain.IceCreamOrder;
import feign.reactive.testcase.domain.OrderGenerator;
import feign.slf4j.Slf4jLogger;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

public class RetryingTest {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static WebClient webClient = WebClient.create();
    private static IcecreamServiceApi clientWithRetry = ReactiveFeign
            .builder()
            .webClient(webClient)
            //encodes body and parameters
            .encoder(new JacksonEncoder(TestUtils.MAPPER))
            .retryer(new ReactiveRetryer.Default())
            .logger(new Slf4jLogger())
            .logLevel(Logger.Level.FULL)
            .target(IcecreamServiceApi.class, "http://localhost:8089");

    @Test
    public void testRetrying_success() throws JsonProcessingException {

        IceCreamOrder orderGenerated = new OrderGenerator().generate(1);
        String orderStr = TestUtils.MAPPER.writeValueAsString(orderGenerated);

        String scenario = "testRetrying_success";
        String orderUrl = "/icecream/orders/1";

        stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "1"))
                .willSetStateTo("attempt1"));

        stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .inScenario(scenario)
                .whenScenarioStateIs("attempt1")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "1"))
                .willSetStateTo("attempt2"));

        stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .inScenario(scenario)
                .whenScenarioStateIs("attempt2")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(orderStr)));

        /* When */
        IceCreamOrder order = clientWithRetry.findOrder(1).block();

        assertThat(order)
                .isEqualToComparingFieldByFieldRecursively(orderGenerated);
    }

    @Test
    public void testRetrying_noMoreAttempts() {

        expectedException.expect(FeignException.class);
        expectedException.expectMessage(containsString("status 503"));

        String orderUrl = "/icecream/orders/1";

        stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Retry-After", "1")));

        /* When */
        clientWithRetry.findOrder(1).block();
    }

}
