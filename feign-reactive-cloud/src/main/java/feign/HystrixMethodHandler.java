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

    private final Target target;
    private final MethodMetadata methodMetadata;
    private final Type returnPublisherType;
    private final ReactiveMethodHandler methodHandler;
    private final Function<Throwable, Object> fallbackFactory;
    private CloudReactiveFeign.SetterFactory commandSetterFactory;

    private HystrixMethodHandler(
            Target target,
            MethodMetadata methodMetadata,
            ReactiveMethodHandler methodHandler,
            CloudReactiveFeign.SetterFactory commandSetterFactory,
            @Nullable Function<Throwable, Object> fallbackFactory) {
        this.target = checkNotNull(target, "target must be not null");;
        this.methodMetadata = checkNotNull(methodMetadata, "methodMetadata must be not null");
        returnPublisherType = ((ParameterizedType) methodMetadata.returnType()).getRawType();
        this.methodHandler = checkNotNull(methodHandler, "methodHandler must be not null");
        this.fallbackFactory = fallbackFactory;
        this.commandSetterFactory = checkNotNull(commandSetterFactory, "hystrixObservableCommandSetter must be not null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) {

        HystrixObservableCommand.Setter setter = commandSetterFactory.create(target, methodMetadata);
        Observable<Object> observable = new HystrixObservableCommand<Object>(setter){
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
        private final CloudReactiveFeign.SetterFactory commandSetterFactory;

        Factory(ReactiveMethodHandlerFactory methodHandlerFactory,
                CloudReactiveFeign.SetterFactory commandSetterFactory,
                @Nullable
                Function<Throwable, Object> fallbackFactory) {
            this.methodHandlerFactory = checkNotNull(methodHandlerFactory, "methodHandlerFactory must not be null");
            this.commandSetterFactory = checkNotNull(commandSetterFactory, "commandSetterFactory must not be null");
            this.fallbackFactory = fallbackFactory;
        }

        @Override
        public ReactiveMethodHandler create(final Target target, final MethodMetadata metadata) {
            return new HystrixMethodHandler(
                    target,
                    metadata,
                    methodHandlerFactory.create(target, metadata),
                    commandSetterFactory,
                    fallbackFactory);
        }
    }
}
