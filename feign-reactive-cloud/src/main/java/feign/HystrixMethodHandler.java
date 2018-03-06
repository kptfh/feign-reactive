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

import static feign.Util.checkNotNull;

/**
 * @author Sergii Karpenko
 */
public class HystrixMethodHandler implements ReactiveMethodHandler {

    private final Type returnPublisherType;
    private final ReactiveMethodHandler methodHandler;
    private final ReactiveMethodHandler fallbackMethodHandler;
    private HystrixObservableCommand.Setter hystrixObservableCommandSetter;

    private HystrixMethodHandler(
            MethodMetadata methodMetadata,
            ReactiveMethodHandler methodHandler,
            @Nullable
                    ReactiveMethodHandler fallbackMethodHandler,
            HystrixObservableCommand.Setter hystrixObservableCommandSetter) {

        returnPublisherType = ((ParameterizedType) methodMetadata.returnType()).getRawType();
        this.methodHandler = checkNotNull(methodHandler, "methodHandler must be not null");
        this.fallbackMethodHandler = fallbackMethodHandler;
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
                return fallbackMethodHandler != null
                        ? RxReactiveStreams.toObservable((Publisher)fallbackMethodHandler.invoke(argv))
                        : super.resumeWithFallback();
            }
        }.toObservable();

        Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

        return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
    }

    static class Factory implements ReactiveMethodHandlerFactory {
        private final ReactiveMethodHandlerFactory methodHandlerFactory;
        private final ReactiveMethodHandlerFactory fallbackMethodHandlerFactory;
        private final HystrixObservableCommand.Setter hystrixObservableCommandSetter;

        Factory(ReactiveMethodHandlerFactory methodHandlerFactory,
                @Nullable
                        ReactiveMethodHandlerFactory fallbackMethodHandlerFactory,
                HystrixObservableCommand.Setter hystrixObservableCommandSetter) {
            this.methodHandlerFactory = checkNotNull(methodHandlerFactory, "methodHandlerFactory must not be null");
            this.fallbackMethodHandlerFactory = fallbackMethodHandlerFactory;
            this.hystrixObservableCommandSetter = checkNotNull(hystrixObservableCommandSetter, "hystrixObservableCommandSetter must not be null");
        }

        @Override
        public ReactiveMethodHandler create(final Target target, final MethodMetadata metadata) {
            return new HystrixMethodHandler(
                    metadata,
                    methodHandlerFactory.create(target, metadata),
                    fallbackMethodHandlerFactory != null ? fallbackMethodHandlerFactory.create(target, metadata) : null,
                    hystrixObservableCommandSetter);
        }
    }
}
