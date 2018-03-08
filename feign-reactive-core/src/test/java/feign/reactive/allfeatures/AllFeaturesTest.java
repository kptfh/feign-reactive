package feign.reactive.allfeatures;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.ReactiveFeign;
import feign.jackson.JacksonEncoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergii Karpenko
 */

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = {AllFeaturesController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class AllFeaturesTest {

    private AllFeaturesApi client;

    @LocalServerPort
    private int port;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void setUp() {
        client = ReactiveFeign.<AllFeaturesApi>builder()
                .webClient(WebClient.create())
                .encoder(new JacksonEncoder(new ObjectMapper()))
                .target(AllFeaturesApi.class, "http://localhost:" + port);
    }

    @Test
    public void shouldReturnAllPassedParameters() {
        Map<String, String> paramMap = new HashMap<String, String>(){
            {put("paramKey", "paramValue");}
        };
        Map<String, String> returned = client.mirrorParameters(777, paramMap).block();

        assertThat(returned).containsEntry("paramInUrl", "777");
        assertThat(returned).containsAllEntriesOf(paramMap);
    }

    @Test
    public void shouldReturnAllPassedParametersNew() {
        Map<String, String> paramMap = new HashMap<String, String>(){
            {put("paramKey", "paramValue");}
        };
//        Map<String, String> returned = client.mirrorParametersNew(777, 888, paramMap).block();
//
//        assertThat(returned).containsEntry("paramInUrl", "777");
//        assertThat(returned).containsEntry("param", "888");
//        assertThat(returned).containsAllEntriesOf(paramMap);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("paramKey", Collections.singletonList("paramValue"));

        URI pageUri = new DefaultUriBuilderFactory()
                .uriString("/mirrorParametersNew?paramInUrl={paramInUrlPlaceholder}")
                .queryParam("param", 888)
                .queryParam("paramInUrl", 999)
                .queryParams(params)
                .build(new HashMap<String, Object>(){
                    {put("paramInUrlPlaceholder", 777);}
                });

        String url = pageUri.toString();

        Map<String, String> response = WebClient.create().method(HttpMethod.GET)
                .uri("http://localhost:" + port+"/mirrorParametersNew?paramInUrl={paramInUrlPlaceholder}",
                        new HashMap<String, Object>(){{put("paramInUrlPlaceholder", 777);}})
                //.(attributes -> attributes.put("param", 888))
                .retrieve().bodyToMono(new HashMap<String, String>().getClass()).block();
        int debug = 0;
    }

    @Test
    public void shouldReturnAllPassedHeaders() {
        Map<String, String> headersMap = new HashMap<String, String>(){
            {put("headerKey1", "headerValue1");
                put("headerKey2", "headerValue2");}
        };
        Map<String, String> returned = client.mirrorHeaders(777, headersMap).block();

        assertThat(returned).containsEntry("Method-Header", "777");
        assertThat(returned).containsAllEntriesOf(headersMap);
        assertThat(returned).containsKey("Accept");
    }

    @Test
    public void shouldReturnBody() {
        String returned = client.mirrorBody("Test Body").block();

        assertThat(returned).isEqualTo("Test Body");
    }

    @Test
    public void shouldReturnBodyMap() {
        Map<String, String> bodyMap = new HashMap<String, String>(){
            {put("key1", "value1");
                put("key2", "value2");}
        };

        Map<String, String> returned = client.mirrorBodyMap(bodyMap).block();
        assertThat(returned).containsAllEntriesOf(bodyMap);
    }

}
