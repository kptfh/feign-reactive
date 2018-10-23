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
package reactivefeign.jetty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import reactivefeign.ReactiveFeign;
import reactivefeign.testcase.IcecreamServiceApi;

/**
 * @author Sergii Karpenko
 */
public class NotFoundTest extends reactivefeign.NotFoundTest {

  @Override
  protected ReactiveFeign.Builder<IcecreamServiceApi> builder() {
    return JettyReactiveFeign.builder();
  }

}
