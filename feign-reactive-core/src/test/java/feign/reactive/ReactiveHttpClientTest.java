package feign.reactive;

import feign.ReactiveFeign;
import feign.jackson.JacksonEncoder;
import feign.reactive.testcase.IcecreamController;
import feign.reactive.testcase.IcecreamServiceApi;
import feign.reactive.testcase.IcecreamServiceApiBroken;
import feign.reactive.testcase.domain.*;
import org.assertj.core.api.Assertions;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Sergii Karpenko
 */

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = {IcecreamController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class ReactiveHttpClientTest {

    private WebClient webClient = WebClient.create();
    private IcecreamServiceApi client;

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
        client = ReactiveFeign.<IcecreamServiceApi>builder()
                .webClient(webClient)
                //encodes body and parameters
                .encoder(new JacksonEncoder(TestUtils.MAPPER))
                .target(IcecreamServiceApi.class, targetUrl);
    }

    @Test
    public void testSimpleGet_success() {

        List<Flavor> flavors = client.getAvailableFlavors().collectList().block();
        List<Mixin> mixins = client.getAvailableMixins().collectList().block();

        assertThat(flavors)
                .hasSize(Flavor.values().length)
                .containsAll(Arrays.asList(Flavor.values()));
        assertThat(mixins)
                .hasSize(Mixin.values().length)
                .containsAll(Arrays.asList(Mixin.values()));

    }

    @Test
    public void testFindOrder_success() {

        IceCreamOrder order = client.findOrder(1).block();
        Assertions.assertThat(order)
                .isEqualToComparingFieldByFieldRecursively(icecreamController.findOrder(1).block());

        Optional<IceCreamOrder> orderOptional = client.findOrder(123).blockOptional();
        assertThat(!orderOptional.isPresent());
    }

    @Test
    public void testMakeOrder_success() {

        IceCreamOrder order = new OrderGenerator().generate(20);

        Bill bill = client.makeOrder(order).block();
        assertThat(bill).isEqualToComparingFieldByField(Bill.makeBill(order));
    }


    @Test
    public void testPayBill_success() {

        Bill bill = Bill.makeBill(new OrderGenerator().generate(30));

        client.payBill(bill).block();
    }

    @Test
    public void testInstantiationContract_forgotProvideWebClient() {

        expectedException.expect(NullPointerException.class);
        expectedException.expectMessage(
                "WebClient instance wasn't provided in ReactiveFeign builder");

        ReactiveFeign.<IcecreamServiceApi>builder()
                .target(IcecreamServiceApi.class, targetUrl);
    }

    @Test
    public void testInstantiationBrokenContract_throwsException() {

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                containsString("IcecreamServiceApiBroken#findOrder(int)"));

        ReactiveFeign.<IcecreamServiceApiBroken>builder()
                .webClient(webClient)
                .target(IcecreamServiceApiBroken.class, targetUrl);
    }
}
