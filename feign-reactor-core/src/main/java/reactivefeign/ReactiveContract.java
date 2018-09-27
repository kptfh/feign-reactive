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

import feign.Contract;
import feign.MethodMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static feign.Util.checkNotNull;

/**
 * Contract allowing only {@link Mono} and {@link Flux} return type.
 *
 * @author Sergii Karpenko
 */
public class ReactiveContract implements Contract {

  private final Contract delegate;

  public ReactiveContract(final Contract delegate) {
    this.delegate = checkNotNull(delegate, "delegate must not be null");
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(final Class<?> targetType) {
    final List<MethodMetadata> methodsMetadata =
        this.delegate.parseAndValidatateMetadata(targetType);

    for (final MethodMetadata metadata : methodsMetadata) {
      final Type type = metadata.returnType();
      if (!isReactorType(type)) {
        throw new IllegalArgumentException(String.format(
            "Method %s of contract %s doesn't returns reactor.core.publisher.Mono or reactor.core.publisher.Flux",
            metadata.configKey(), targetType.getSimpleName()));
      }
    }

    return methodsMetadata;
  }

  private boolean isReactorType(final Type type) {
    return (type instanceof ParameterizedType)
        && (((ParameterizedType) type).getRawType() == Mono.class
            || ((ParameterizedType) type).getRawType() == Flux.class);
  }
}
