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

import feign.RetryableException;

import java.util.Date;

/**
 * @author Sergii Karpenko
 */
public class ReactiveRetryers {

  public static ReactiveRetryPolicy retry(int maxRetries) {
    return (error, attemptNo) -> attemptNo <= maxRetries ? 0 : -1;
  }

  public static ReactiveRetryPolicy retryWithBackoff(int maxRetries, long periodInMs) {
    return (error, attemptNo) -> {
      if (attemptNo <= maxRetries) {
        long delay;
        Date retryAfter;
        // "Retry-After" header set
        if (error instanceof RetryableException
            && (retryAfter = ((RetryableException) error)
                .retryAfter()) != null) {
          delay = retryAfter.getTime() - System.currentTimeMillis();
          delay = Math.min(delay, periodInMs);
          delay = Math.max(delay, 0);
        } else {
          delay = periodInMs;
        }
        return delay;
      } else {
        return -1;
      }
    };
  }

}
