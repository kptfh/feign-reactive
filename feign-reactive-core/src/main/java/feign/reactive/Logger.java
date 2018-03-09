/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.reactive;

import feign.reactive.client.ReactiveRequest;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.util.function.Supplier;
import java.util.stream.Collectors;

import static feign.reactive.Logger.MessageSupplier.msg;

/**
 * @author Sergii Karpenko
 */

public class Logger {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(ReactiveClientMethodHandler.class);

    public void logRequest(String feignMethodTag, ReactiveRequest request) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}]--->{} {} HTTP/1.1", feignMethodTag, request.method(), request.uri());
        }

        if (logger.isTraceEnabled()) {
            logger.trace("[{}] REQUEST HEADERS\n{}",
                    feignMethodTag,
                    msg(() -> request.headers().entrySet().stream()
                            .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
                            .collect(Collectors.joining("\n"))));

            request.body().subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(Subscription subscription) {}

                @Override
                public void onNext(Object body) {
                    logger.trace("[{}] REQUEST BODY\n{}", feignMethodTag, body);
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
            });

        }
    }

    public void logResponseHeaders(String feignMethodTag, HttpHeaders httpHeaders) {
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] RESPONSE HEADERS\n{}",
                    feignMethodTag,
                    msg(() -> httpHeaders.entrySet().stream()
                            .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
                            .collect(Collectors.joining("\n"))));
        }
    }

    public void logResponse(String feignMethodTag, Object response, long elapsedTime) {
        if (logger.isTraceEnabled()) {
            logger.debug("[{}]<---{}", feignMethodTag, response);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[{}]<--- takes {} milliseconds", feignMethodTag, elapsedTime);
        }
    }

    public void logRetry(String feignMethodTag) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}]---> RETRYING", feignMethodTag);
        }
    }

    public static class MessageSupplier {
        private Supplier<?> supplier;

        public MessageSupplier(Supplier<?> supplier) {
            this.supplier = supplier;
        }

        @Override
        public String toString() {
            return supplier.get().toString();
        }

        public static MessageSupplier msg(Supplier<?> supplier) {
            return new MessageSupplier(supplier);
        }
    }

}
