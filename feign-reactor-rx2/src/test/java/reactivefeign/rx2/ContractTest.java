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
package reactivefeign.rx2;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import reactivefeign.ReactiveFeign;
import reactivefeign.rx2.testcase.IcecreamServiceApi;
import reactivefeign.rx2.testcase.IcecreamServiceApiBroken;

import static org.hamcrest.Matchers.containsString;

/**
 * @author Sergii Karpenko
 */

public class ContractTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  protected <T> ReactiveFeign.Builder<T> builder(){
    return Rx2ReactiveFeign.builder();
  }

  @Test
  public void shouldFailOnBrokenContract() {

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(containsString("Broken Contract"));

    this.<IcecreamServiceApi>builder()
        .contract(targetType -> {
          throw new IllegalArgumentException("Broken Contract");
        })
        .target(IcecreamServiceApi.class, "http://localhost:8888");
  }

  @Test
  public void shouldFailIfNotReactiveContract() {

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(containsString("IcecreamServiceApiBroken#findOrder(int)"));

    this.<IcecreamServiceApiBroken>builder()
        .target(IcecreamServiceApiBroken.class, "http://localhost:8888");
  }

}
