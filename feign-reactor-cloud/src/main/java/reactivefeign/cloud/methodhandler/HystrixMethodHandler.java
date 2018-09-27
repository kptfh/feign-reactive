package reactivefeign.cloud.methodhandler;

import com.netflix.hystrix.HystrixObservableCommand;
import feign.MethodMetadata;
import feign.Target;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactivefeign.cloud.CloudReactiveFeign;
import reactivefeign.methodhandler.MethodHandler;
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
public class HystrixMethodHandler implements MethodHandler {

    private final Method method;
    private final Type returnPublisherType;
    private final MethodHandler methodHandler;
    private final Function<Throwable, Object> fallbackFactory;
    private final HystrixObservableCommand.Setter hystrixObservableCommandSetter;

    HystrixMethodHandler(
            Target target, MethodMetadata methodMetadata,
            MethodHandler methodHandler,
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
    public Publisher<Object> invoke(final Object[] argv) {

        Observable<Object> observable = new HystrixObservableCommand<Object>(hystrixObservableCommandSetter) {
            @Override
            protected Observable<Object> construct() {
                Publisher publisher;
                try {
                    publisher = (Publisher) methodHandler.invoke(argv);
                } catch (Throwable throwable) {
                    publisher = Mono.error(throwable);
                }
                return RxReactiveStreams.toObservable(publisher);
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

        if(returnPublisherType == Mono.class){
            return Mono.from(publisher);
        } else if(returnPublisherType == Flux.class){
            return Flux.from(publisher);
        } else {
            throw new IllegalArgumentException("Unknown returnPublisherType: " + returnPublisherType);
        }
    }

    protected Object getFallbackValue(Object target, Method method, Object[] argv) throws Throwable {
        return method.invoke(target, argv);
    }
}
