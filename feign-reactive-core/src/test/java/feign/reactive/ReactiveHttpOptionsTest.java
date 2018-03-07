package feign.reactive;

import feign.ReactiveFeign;
import feign.Request;
import feign.jackson.JacksonEncoder;
import feign.reactive.testcase.IcecreamController;
import feign.reactive.testcase.IcecreamServiceApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test the new capability of Reactive Feign client to support both Feign
 * Request.Options (regression) and the new ReactiveOptions configuration.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = {IcecreamController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class ReactiveHttpOptionsTest {

    private WebClient webClient = WebClient.create();

    @Autowired
    private IcecreamController icecreamController;

    @LocalServerPort
    private int port;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String targetUrl;

    @Before
    public void setUp() {
        targetUrl = "http://localhost:" + port;
    }

    /**
     * Test the ReactiveOptions version of the Reactive Feign Client.
     * This is useful for use-cases like HTTPS/2 or gzip compressed responses.
     */
    @Test
    public void testReactiveOptions() {

        ReactiveOptions options = new ReactiveOptions(
                5000, 5000, true
        );

        IcecreamServiceApi client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(webClient)
                .options(options)
                //encodes body and parameters
                .encoder(new JacksonEncoder(TestUtils.MAPPER))
                .target(IcecreamServiceApi.class, targetUrl);

        testClient(client);
    }


    /**
     * Test the Feign Request Options version of the Vert.x Feign Client.
     * This proves regression is not broken for existing use-cases.
     */
    @Test
    public void testRequestOptions() {

        // Plain old Feign Request.Options (regression)
        Request.Options options = new Request.Options(5000, 5000);

        IcecreamServiceApi client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(webClient)
                .options(options)
                //encodes body and parameters
                .encoder(new JacksonEncoder(TestUtils.MAPPER))
                .target(IcecreamServiceApi.class, targetUrl);

        testClient(client);
    }


    /**
     * Test the provided client for the correct results
     *
     * @param client Feign client instance
     */
    private void testClient(IcecreamServiceApi client) {
        client.getAvailableFlavors().collectList().block();
    }
}
