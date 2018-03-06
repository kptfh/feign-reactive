package feign;

import feign.reactive.ReactiveMethodHandlerFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Sergii Karpenko
 */
public class FallbackMethodHandlerFactory implements ReactiveMethodHandlerFactory{

    private final Object fallbackInstance;

    public FallbackMethodHandlerFactory(Object fallbackInstance) {
        this.fallbackInstance = fallbackInstance;
    }

    @Override
    public ReactiveMethodHandler create(Target target, MethodMetadata metadata) {
        return argv -> {
            try {
                return Mono.just(getMethod(target, metadata).invoke(fallbackInstance, argv));
            } catch (Exception e) {
                return Mono.error(e);
            }
        };
    }

    public Method getMethod(Target target, MethodMetadata metadata){
        if(!target.type().isInstance(fallbackInstance)){

        }
    }
}
