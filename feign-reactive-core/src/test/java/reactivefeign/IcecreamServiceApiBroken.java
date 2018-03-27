package reactivefeign;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import reactivefeign.testcase.domain.Bill;
import reactivefeign.testcase.domain.Flavor;
import reactivefeign.testcase.domain.IceCreamOrder;
import reactivefeign.testcase.domain.Mixin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * API of an iceream web service with one method that doesn't returns
 * {@link Mono} or {@link Flux} and violates {@link ReactiveDelegatingContract}s rules.
 *
 * @author Sergii Karpenko
 */
public interface IcecreamServiceApiBroken {

    @RequestLine("GET /icecream/flavors")
    Mono<Collection<Flavor>> getAvailableFlavors();

    @RequestLine("GET /icecream/mixins")
    Mono<Collection<Mixin>> getAvailableMixins();

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    Mono<Bill> makeOrder(IceCreamOrder order);

    /**
     * Method that doesn't respects contract.
     */
    @RequestLine("GET /icecream/orders/{orderId}")
    IceCreamOrder findOrder(@Param("orderId") int orderId);
}
