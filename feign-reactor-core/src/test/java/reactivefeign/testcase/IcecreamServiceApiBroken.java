/**
 * Copyright 2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package reactivefeign.testcase;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import reactivefeign.ReactiveDelegatingContract;
import reactivefeign.testcase.domain.Bill;
import reactivefeign.testcase.domain.Flavor;
import reactivefeign.testcase.domain.IceCreamOrder;
import reactivefeign.testcase.domain.Mixin;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * API of an iceream web service with one method that doesn't returns {@link Mono} or {@link Flux}
 * and violates {@link ReactiveDelegatingContract}s rules.
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
