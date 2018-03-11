package feign.reactive.client;

import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

import static feign.reactive.ReactiveUtils.onNext;

public class RetryReactiveClient implements ReactiveClient{

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RetryReactiveClient.class);

    private final ReactiveClient reactiveClient;
    private final Function<Flux<Throwable>, Publisher<?>> retryFunction;
    private final Type returnPublisherType;

    public RetryReactiveClient(ReactiveClient reactiveClient,
                               MethodMetadata methodMetadata,
                               Function<Flux<Throwable>, Publisher<?>> retryFunction) {
        this.reactiveClient = reactiveClient;
        String methodTag = methodMetadata.configKey().substring(0, methodMetadata.configKey().indexOf('('));
        this.retryFunction = wrapWithLog(retryFunction, methodTag);
        final Type returnType = methodMetadata.returnType();
        returnPublisherType = ((ParameterizedType) returnType).getRawType();
    }

    @Override
    public Publisher<Object> executeRequest(ReactiveRequest request) {
        Publisher<Object> objectPublisher = reactiveClient.executeRequest(request);
        if(returnPublisherType == Mono.class){
            return ((Mono<Object>) objectPublisher)
                    .retryWhen(retryFunction);
        } else {
            return ((Flux<Object>) objectPublisher)
                    .retryWhen(retryFunction);
        }
    }

    private static Function<Flux<Throwable>, Publisher<?>> wrapWithLog(
            Function<Flux<Throwable>, Publisher<?>> retryFunction,
            String feignMethodTag){
        return throwableFlux -> {
            Publisher<?> publisher = retryFunction.apply(throwableFlux);
            publisher.subscribe(onNext(t -> {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("[{}]---> RETRYING", feignMethodTag);
                                }
                            }
                    ));
            return publisher;
        };
    }
}
