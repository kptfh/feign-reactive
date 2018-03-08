package feign.reactive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
public class TestController implements TestInterface{

    @GetMapping(path = "/mirror")
    @Override
    public Mono<Long> get(@RequestParam(VALUE_PARAMETER) long valueToReturn) {
//        return Mono.just(valueToReturn);
        return Mono.delay(Duration.ofMillis(5000)).map(aLong -> valueToReturn);
    }
}
