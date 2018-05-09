package reactivefeign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.testcase.IcecreamServiceApi;
import reactivefeign.testcase.domain.IceCreamOrder;
import reactivefeign.testcase.domain.OrderGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Java6Assertions.assertThat;

public class RequestInterceptorTest {

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(
            wireMockConfig().dynamicPort());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void resetServers() {
        wireMockRule.resetAll();
    }

    @Test
    public void shouldProcessDefaultMethodOnProxy() throws JsonProcessingException {

        String orderUrl = "/icecream/orders/1";

        IceCreamOrder orderGenerated = new OrderGenerator().generate(1);
        String orderStr = TestUtils.MAPPER.writeValueAsString(orderGenerated);

        wireMockRule.stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Bearer mytoken123"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(orderStr)));

        wireMockRule.stubFor(get(urlEqualTo(orderUrl))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse().withStatus(HttpStatus.SC_UNAUTHORIZED)
                        .withHeader("Content-Type", "application/json")
                        .withBody(orderStr)));

        IcecreamServiceApi client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(WebClient.create())
                .target(IcecreamServiceApi.class,
                        "http://localhost:" + wireMockRule.port());

        IceCreamOrder firstOrder = client.findFirstOrder().block();

        assertThat(firstOrder).isEqualToComparingFieldByField(orderGenerated);
    }
}
