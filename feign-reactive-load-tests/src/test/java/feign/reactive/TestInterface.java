package feign.reactive;


import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

public interface TestInterface {

    String VALUE_PARAMETER = "value";

    @RequestLine("GET /mirror?" + VALUE_PARAMETER + "={value}")
    Mono<Integer> get(@Param("value") int valueToReturn);

}
