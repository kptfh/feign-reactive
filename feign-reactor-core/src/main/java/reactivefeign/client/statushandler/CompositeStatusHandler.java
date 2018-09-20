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
package reactivefeign.client.statushandler;

import reactivefeign.client.ReactiveHttpResponse;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * @author Sergii Karpenko
 */
public class CompositeStatusHandler implements ReactiveStatusHandler {

  private final List<ReactiveStatusHandler> handlers;

  public static CompositeStatusHandler compose(ReactiveStatusHandler... handlers) {
    return new CompositeStatusHandler(asList(handlers));
  }

  private CompositeStatusHandler(List<ReactiveStatusHandler> handlers) {
    this.handlers = handlers;
  }

  @Override
  public boolean shouldHandle(int status) {
    return handlers.stream().anyMatch(handler -> handler.shouldHandle(status));
  }

  @Override
  public Mono<? extends Throwable> decode(String methodKey, ReactiveHttpResponse response) {
    return handlers.stream()
        .filter(statusHandler -> statusHandler
            .shouldHandle(response.status()))
        .findFirst()
        .map(statusHandler -> statusHandler.decode(methodKey, response))
        .orElse(null);
  }
}
