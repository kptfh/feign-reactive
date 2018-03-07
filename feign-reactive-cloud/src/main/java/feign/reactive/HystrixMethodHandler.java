package feign.reactive;

import com.netflix.hystrix.HystrixObservableCommand;
import feign.CloudReactiveFeign;
import feign.MethodMetadata;
import feign.Target;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Function;

import static feign.Feign.configKey;
import static feign.Util.checkNotNull;

/**
 * @author Sergii Karpenko
 */
public class HystrixMethodHandler implements ReactiveMethodHandler {

    private final Method method;
    private final Type returnPublisherType;
    private final ReactiveMethodHandler methodHandler;
    private final Function<Throwable, Object> fallbackFactory;
    private final HystrixObservableCommand.Setter hystrixObservableCommandSetter;

    private HystrixMethodHandler(
            Target target, MethodMetadata methodMetadata,
            ReactiveMethodHandler methodHandler,
            CloudReactiveFeign.SetterFactory setterFactory,
            @Nullable
                    Function<Throwable, Object> fallbackFactory) {
        checkNotNull(target, "target must be not null");

        checkNotNull(methodMetadata, "methodMetadata must be not null");
        method = Arrays.stream(target.type().getMethods())
                .filter(method -> configKey(target.type(), method).equals(methodMetadata.configKey()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException());
        method.setAccessible(true);

        returnPublisherType = ((ParameterizedType) methodMetadata.returnType()).getRawType();
        this.methodHandler = checkNotNull(methodHandler, "methodHandler must be not null");
        this.fallbackFactory = fallbackFactory;
        checkNotNull(setterFactory, "setterFactory must be not null");
        hystrixObservableCommandSetter = setterFactory.create(target, methodMetadata);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) {

        Observable<Object> observable = new HystrixObservableCommand<Object>(hystrixObservableCommandSetter) {
            @Override
            protected Observable<Object> construct() {
                return RxReactiveStreams.toObservable((Publisher) methodHandler.invoke(argv));
            }

            @Override
            protected Observable<Object> resumeWithFallback() {
                if (fallbackFactory != null) {
                    Object fallback = fallbackFactory.apply(getExecutionException());
                    try {
                        Object fallbackValue = getFallbackValue(fallback, method, argv);
                        return RxReactiveStreams.toObservable((Publisher<Object>) fallbackValue);
                    } catch (Throwable e) {
                        return Observable.error(e);
                    }

                } else {
                    return super.resumeWithFallback();
                }
            }
        }.toObservable();

        Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

        return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
    }

    protected Object getFallbackValue(Object target, Method method, Object[] argv) throws Throwable {
        return method.invoke(target, argv);
    }

    public static class Factory implements ReactiveMethodHandlerFactory {
        private final ReactiveMethodHandlerFactory methodHandlerFactory;
        private final CloudReactiveFeign.SetterFactory commandSetterFactory;
        private final Function<Throwable, Object> fallbackFactory;

        public Factory(ReactiveMethodHandlerFactory methodHandlerFactory,
                CloudReactiveFeign.SetterFactory commandSetterFactory,
                @Nullable
                        Function<Throwable, Object> fallbackFactory) {
            this.methodHandlerFactory = checkNotNull(methodHandlerFactory, "methodHandlerFactory must not be null");
            this.commandSetterFactory = checkNotNull(commandSetterFactory, "hystrixObservableCommandSetter must not be null");
            this.fallbackFactory = fallbackFactory;
        }

        @Override
        public ReactiveMethodHandler create(final Target target, final MethodMetadata metadata) {
            return new HystrixMethodHandler(
                    target, metadata,
                    methodHandlerFactory.create(target, metadata),
                    commandSetterFactory,
                    fallbackFactory);
        }
    }
}
