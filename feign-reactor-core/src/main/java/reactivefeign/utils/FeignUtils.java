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
package reactivefeign.utils;

import feign.MethodMetadata;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class FeignUtils {

  public static String methodTag(MethodMetadata methodMetadata) {
    return methodMetadata.configKey().substring(0,
        methodMetadata.configKey().indexOf('('));
  }

  public static Type returnPublisherType(MethodMetadata methodMetadata) {
    final Type returnType = methodMetadata.returnType();
    return ((ParameterizedType) returnType).getRawType();
  }
}
