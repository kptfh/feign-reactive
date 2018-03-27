package reactivefeign.allfeatures;

import org.reactivestreams.Publisher;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static reactor.core.publisher.Mono.just;

@RestController
public class AllFeaturesController implements AllFeaturesApi{

    @GetMapping(path = "/mirrorParameters")
    @Override
    public Mono<Map<String, String>> mirrorParameters(
            @RequestParam("paramInUrl") long paramInUrl,
            @RequestParam Map<String, String> paramMap) {
        paramMap.put("paramInUrl", Long.toString(paramInUrl));
        return just(paramMap);
    }


    @GetMapping(path = "/mirrorParametersNew")
    @Override
    public Mono<Map<String, String>> mirrorParametersNew(
            @RequestParam("paramInUrl") long paramInUrl,
            @RequestParam("param") long param,
            @RequestParam Map<String, String> paramMap) {
        paramMap.put("paramInUrl", Long.toString(paramInUrl));
        paramMap.put("param", Long.toString(param));
        return just(paramMap);
    }
    @GetMapping(path = "/mirrorHeaders")
    @Override
    public Mono<Map<String, String>> mirrorHeaders(
            @RequestHeader("Method-Header") long param,
            @RequestHeader Map<String, String> headersMap) {
        return just(headersMap);
    }

    @PostMapping(path = "/mirrorBody")
    @Override
    public Mono<String> mirrorBody(
            @RequestBody String body) {
        return just(body);
    }

    @PostMapping(path = "/mirrorBodyMap")
    @Override
    public Mono<Map<String, String>> mirrorBodyMap(
            @RequestBody Map<String, String> body) {
        return just(body);
    }

    @PostMapping(path = "/mirrorBodyReactive")
    @Override
    public Mono<String> mirrorBodyReactive(
            @RequestBody Publisher<String> body) {
        return Mono.from(body);
    }

    @PostMapping(path = "/mirrorBodyMapReactive")
    @Override
    public Mono<Map<String, String>> mirrorBodyMapReactive(
            @RequestBody Publisher<Map<String, String>> body) {
        return Mono.from(body);
    }

    @PostMapping(path = "/mirrorBodyStream")
    @Override
    public Flux<TestObject> mirrorBodyStream(
            @RequestBody Publisher<TestObject> bodyStream) {
        return Flux.from(bodyStream);
    }

}
