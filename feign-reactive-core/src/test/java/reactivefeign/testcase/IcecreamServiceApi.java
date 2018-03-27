package reactivefeign.testcase;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import reactivefeign.testcase.domain.Bill;
import reactivefeign.testcase.domain.Flavor;
import reactivefeign.testcase.domain.Mixin;
import reactivefeign.testcase.domain.IceCreamOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API of an iceream web service.
 *
 * @author Sergii Karpenko
 */
@Headers({"Accept: application/json"})
public interface IcecreamServiceApi {

    @RequestLine("GET /icecream/flavors")
    Flux<Flavor> getAvailableFlavors();

    @RequestLine("GET /icecream/mixins")
    Flux<Mixin> getAvailableMixins();

    @RequestLine("POST /icecream/orders")
    @Headers("Content-Type: application/json")
    Mono<Bill> makeOrder(IceCreamOrder order);

    @RequestLine("GET /icecream/orders/{orderId}")
    Mono<IceCreamOrder> findOrder(@Param("orderId") int orderId);

    @RequestLine("POST /icecream/bills/pay")
    @Headers("Content-Type: application/json")
    Mono<Void> payBill(Bill bill);
}
