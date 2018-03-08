package feign.reactive.allfeatures;

import feign.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Headers({"Accept: application/json"})
public interface AllFeaturesApi {

    @RequestLine("GET /mirrorParameters?paramInUrl={paramInUrlPlaceholder}")
    Mono<Map<String, String>> mirrorParameters(
            @Param("paramInUrlPlaceholder") long paramInUrl,
            @QueryMap Map<String, String> paramMap);

    @RequestLine("GET /mirrorParametersNew?paramInUrl={paramInUrlPlaceholder}")
    Mono<Map<String, String>> mirrorParametersNew(
            @Param("paramInUrlPlaceholder") long paramInUrl,
            @Param("param") long param,
            @QueryMap Map<String, String> paramMap);

    @RequestLine("GET /mirrorHeaders")
    @Headers({"Method-Header: {headerValue}"})
    Mono<Map<String, String>> mirrorHeaders(
            @Param("headerValue") long param,
            @HeaderMap Map<String, String> paramMap);

    @RequestLine("POST " + "/mirrorBody")
    Mono<String> mirrorBody(String body);

    @RequestLine("POST " + "/mirrorBodyMap")
    @Headers({"Content-Type: application/json"})
    Mono<Map<String, String>> mirrorBodyMap(
            Map<String, String> body);

}
