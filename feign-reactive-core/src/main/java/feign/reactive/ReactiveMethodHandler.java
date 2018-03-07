package feign.reactive;

import feign.InvocationHandlerFactory;
import org.reactivestreams.Publisher;

/**
 * @author Sergii Karpenko
 */
public interface ReactiveMethodHandler extends InvocationHandlerFactory.MethodHandler {

    @Override
    Publisher<Object> invoke(final Object[] argv);

}
