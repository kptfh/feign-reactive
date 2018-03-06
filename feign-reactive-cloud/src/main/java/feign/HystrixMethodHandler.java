package feign;

import com.netflix.hystrix.HystrixObservableCommand;
import feign.reactive.ReactiveMethodHandlerFactory;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

import static feign.Util.checkNotNull;

/**
 * @author Sergii Karpenko
 */
public class HystrixMethodHandler implements ReactiveMethodHandler {

    private final MethodMetadata methodMetadata;
    private final Type returnPublisherType;
    private final ReactiveMethodHandler methodHandler;
    private final Function<Throwable, Object> fallbackFactory;
    private HystrixObservableCommand.Setter hystrixObservableCommandSetter;

    private HystrixMethodHandler(
            MethodMetadata methodMetadata,
            ReactiveMethodHandler methodHandler,
            @Nullable
                    Function<Throwable, Object> fallbackFactory,
            HystrixObservableCommand.Setter hystrixObservableCommandSetter) {
        this.methodMetadata = checkNotNull(methodMetadata, "methodMetadata must be not null");
        returnPublisherType = ((ParameterizedType) methodMetadata.returnType()).getRawType();
        this.methodHandler = checkNotNull(methodHandler, "methodHandler must be not null");
        this.fallbackFactory = fallbackFactory;
        this.hystrixObservableCommandSetter = checkNotNull(hystrixObservableCommandSetter, "hystrixObservableCommandSetter must be not null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) {

        Observable<Object> observable = new HystrixObservableCommand<Object>(hystrixObservableCommandSetter){
            @Override
            protected Observable<Object> construct() {
                return RxReactiveStreams.toObservable((Publisher)methodHandler.invoke(argv));
            }

            @Override
            protected Observable<Object> resumeWithFallback() {
                if(fallbackFactory != null){
                    Object fallback = fallbackFactory.apply(getExecutionException());
                    Object fallbackValue = getFallbackValue(fallback, argv);
                    return Observable.just(fallbackValue);
                } else {
                    return super.resumeWithFallback();
                }
            }
        }.toObservable();

        Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

        return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
    }

    protected Object getFallbackValue(Object fallback, final Object[] argv){
        //TODO implement
        return null;
    }

    static class Factory implements ReactiveMethodHandlerFactory {
        private final ReactiveMethodHandlerFactory methodHandlerFactory;
        private final Function<Throwable, Object> fallbackFactory;
        private final HystrixObservableCommand.Setter hystrixObservableCommandSetter;

        Factory(ReactiveMethodHandlerFactory methodHandlerFactory,
                @Nullable
                Function<Throwable, Object> fallbackFactory,
                HystrixObservableCommand.Setter hystrixObservableCommandSetter) {
            this.methodHandlerFactory = checkNotNull(methodHandlerFactory, "methodHandlerFactory must not be null");
            this.fallbackFactory = fallbackFactory;
            this.hystrixObservableCommandSetter = checkNotNull(hystrixObservableCommandSetter, "hystrixObservableCommandSetter must not be null");
        }

        @Override
        public ReactiveMethodHandler create(final Target target, final MethodMetadata metadata) {
            return new HystrixMethodHandler(
                    metadata,
                    methodHandlerFactory.create(target, metadata),
                    fallbackFactory,
                    hystrixObservableCommandSetter);
        }
    }
}
