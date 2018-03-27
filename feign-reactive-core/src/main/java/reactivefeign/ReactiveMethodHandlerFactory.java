package reactivefeign;


import feign.MethodMetadata;
import feign.Target;

public interface ReactiveMethodHandlerFactory {

    ReactiveMethodHandler create(final Target target, final MethodMetadata metadata);
}
