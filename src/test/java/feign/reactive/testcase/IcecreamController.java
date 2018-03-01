package feign.reactive.testcase;


import feign.reactive.testcase.domain.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;


/**
 * Controller of an iceream web service.
 *
 * @author Sergii Karpenko
 */
@RestController
public class IcecreamController implements IcecreamServiceApi{

    private OrderGenerator generator = new OrderGenerator();
    private Map<Integer, IceCreamOrder> orders;

    IcecreamController(){
        orders = generator.generateRange(10).stream().collect(Collectors.toMap(
                IceCreamOrder::getId, o -> o
        ));
    }

    @GetMapping(path = "/icecream/flavors")
    @Override
    public Flux<Flavor> getAvailableFlavors() {
        return Flux.fromArray(Flavor.values());
    }

    @GetMapping(path = "/icecream/mixins")
    @Override
    public Flux<Mixin> getAvailableMixins() {
        return Flux.fromArray(Mixin.values());
    }

    @GetMapping(path = "/icecream/orders/{orderId}")
    @Override
    public Mono<IceCreamOrder> findOrder(@PathVariable("orderId") int orderId) {
        IceCreamOrder order = orders.get(orderId);
        return order != null ? just(order) : empty();
    }

    @PostMapping(path = "/icecream/orders")
    @Override
    public Mono<Bill> makeOrder(@RequestBody IceCreamOrder order) {
        return just(Bill.makeBill(order));
    }

    @PostMapping(path = "/icecream/bills/pay")
    @Override
    public Mono<Void> payBill(@RequestBody Bill bill) {
        return empty();
    }
}
