package feign.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import feign.ReactiveFeign;
import feign.jackson.JacksonEncoder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static feign.reactive.TestInterface.VALUE_PARAMETER;

/**
 * Should check that client is not blocking
 *
 * @author Sergii Karpenko
 */
public class ReactiveFeignLoadTest {

    @ClassRule
    public static WireMockClassRule server = new WireMockClassRule(wireMockConfig().dynamicPort()
            // Enable asynchronous request processing in Jetty. Recommended when using WireMock for performance testing with delays, as it allows much more efficient use of container threads and therefore higher throughput. Defaults to false.
            .asynchronousResponseEnabled(true)
            // Set the number of asynchronous response threads. Effective only with asynchronousResponseEnabled=true. Defaults to 10.
            .asynchronousResponseThreads(10)
            .extensions(new ResponseDefinitionTransformer() {
                @Override
                public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files, Parameters parameters) {
                    return new ResponseDefinitionBuilder()
                            .withHeader("Content-Type", "application/json")
                            .withStatus(200)
                            .withBody(request.queryParameter(VALUE_PARAMETER).values().get(0))
                            .build();
                }

                @Override
                public String getName() {
                    return "testTransformer";
                }

                @Override
                public boolean applyGlobally() {
                    return false;
                }
            })
    );

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void mockServer() {
        server.stubFor(get(urlPathEqualTo("/mirror"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("testTransformer")
                ));
    }

    @Test
    public void shouldNotBlock() throws IOException, InterruptedException {

        mockServer();

        TestInterface client = ReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .target(TestInterface.class, "http://localhost:" + server.port());

        performLoadTest(client);
    }

    private void performLoadTest(TestInterface client){

        for(int i = 0; i < 1000; i++){
            client.get(i).subscribe(
                    integer -> System.out.println("********************"+integer+":"+Thread.activeCount())
            );
        }

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
