package feign.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.CloudReactiveFeign;
import feign.RequestLine;
import feign.jackson.JacksonEncoder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;

import static com.netflix.client.ClientFactory.getNamedLoadBalancer;
import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.assertj.core.api.Assertions.assertThat;


public class CloudReactiveHttpClientTest {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    private static String serviceName = "LoadBalancingTargetTest-loadBalancingDefaultPolicyRoundRobin";
    private static TestInterface client;

    static String hostAndPort(URL url) {
        return "localhost:" + url.getPort();
    }

    @Test
    public void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
        String body = "success!";
        server1.enqueue(new MockResponse().setBody(body));
        server2.enqueue(new MockResponse().setBody(body));

        String serverListKey = serviceName + ".ribbon.listOfServers";
        getConfigInstance().setProperty(serverListKey,
                hostAndPort(server1.url("").url()) + "," + hostAndPort(
                        server2.url("").url()));

        TestInterface client = CloudReactiveFeign
                .builder()
                .webClient(WebClient.create())
                //encodes body and parameters
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .setLoadBalancerCommand(
                        LoadBalancerCommand.builder()
                                .withLoadBalancer(AbstractLoadBalancer.class.cast(getNamedLoadBalancer(serviceName)))
                                .build()
                )
                .target(TestInterface.class, "http://"+serviceName);

        try {

            String result1 = client.get().block();
            String result2 = client.get().block();

            assertThat(result1)
                    .isEqualTo(result2)
                    .isEqualTo(body);

            assertThat(server1.takeRequest().getPath())
                    .isEqualTo(server2.takeRequest().getPath())
                    .isEqualTo("/");
            assertThat(server1.getRequestCount())
                    .isEqualTo(server2.getRequestCount())
                    .isEqualTo(1);
        } finally {
            getConfigInstance().clearProperty(serverListKey);
        }
    }


    interface TestInterface {

        @RequestLine("GET /")
        Mono<String> get();
    }
}
