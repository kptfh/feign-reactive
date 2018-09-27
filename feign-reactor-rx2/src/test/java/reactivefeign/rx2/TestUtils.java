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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.function.Predicate;


/**
 * Helper methods for tests.
 */
class TestUtils {
  static final ObjectMapper MAPPER;

  static {
    MAPPER = new ObjectMapper();
    MAPPER.registerModule(new JavaTimeModule());
  }

  public static <T> Predicate<T> equalsComparingFieldByFieldRecursively(T rhs) {
    return lhs -> {
      try {
        return MAPPER.writeValueAsString(lhs).equals(MAPPER.writeValueAsString(rhs));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static <T> io.reactivex.functions.Predicate<T> equalsComparingFieldByFieldRecursivelyRx(T rhs) {
    return lhs -> {
      try {
        return MAPPER.writeValueAsString(lhs).equals(MAPPER.writeValueAsString(rhs));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    };
  }
}
