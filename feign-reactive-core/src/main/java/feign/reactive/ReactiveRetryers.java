package feign.reactive;

import feign.RetryableException;
import feign.reactive.client.WebReactiveClient;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.function.Function;

/**
 * @author Sergii Karpenko
 */
public class ReactiveRetryers {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WebReactiveClient.class);

    public static Function<Flux<Throwable>, Publisher<?>> retryWithDelay(
            int maxAttempts, long period) {
        return companion -> companion
                .zipWith(Flux.range(1, maxAttempts), (error, index) -> {
                    if (index < maxAttempts) {

                        long delay;
                        Date retryAfter;
                        if (error instanceof RetryableException && (retryAfter = ((RetryableException) error).retryAfter()) != null) {
                            delay = retryAfter.getTime() - System.currentTimeMillis();
                            delay = Math.min(delay, period);
                            delay = Math.max(delay, 0);
                        } else {
                            delay = period;
                        }

                        return Mono.delay(Duration.ofMillis(delay))
                                .doOnNext(aLong -> {
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("[{}]---> RETRYING on error", error);
                                    }
                                });
                    } else throw Exceptions.propagate(error);
                });
    }

}
