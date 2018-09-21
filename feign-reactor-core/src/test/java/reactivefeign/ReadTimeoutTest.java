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
package reactivefeign;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.ClassRule;
import org.junit.Test;
import reactivefeign.client.ReadTimeoutException;
import reactivefeign.testcase.IcecreamServiceApi;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author Sergii Karpenko
 */
abstract public class ReadTimeoutTest {

  @ClassRule
  public static WireMockClassRule wireMockRule = new WireMockClassRule(
      wireMockConfig().dynamicPort());

  abstract protected ReactiveFeign.Builder<IcecreamServiceApi> builder(ReactiveOptions options);

  @Test
  public void shouldFailOnReadTimeout() {

    String orderUrl = "/icecream/orders/1";

    wireMockRule.stubFor(get(urlEqualTo(orderUrl))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withFixedDelay(200)));

    IcecreamServiceApi client = builder(
        new ReactiveOptions.Builder()
            .setConnectTimeoutMillis(300)
            .setReadTimeoutMillis(100)
            .build())
                .target(IcecreamServiceApi.class,
                    "http://localhost:" + wireMockRule.port());

    StepVerifier.create(client.findOrder(1))
        .expectError(ReadTimeoutException.class)
        .verify();
  }
}
