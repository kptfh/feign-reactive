package reactivefeign.rx2.methodhandler;

import feign.MethodMetadata;
import feign.Target;
import io.reactivex.BackpressureStrategy;
import reactivefeign.methodhandler.DefaultMethodHandler;
import reactivefeign.methodhandler.MethodHandler;
import reactivefeign.methodhandler.MethodHandlerFactory;
import reactivefeign.publisher.PublisherClientFactory;

import java.lang.reflect.Method;

import static reactivefeign.utils.FeignUtils.returnPublisherType;

public class Rx2MethodHandlerFactory implements MethodHandlerFactory {

    private final PublisherClientFactory publisherClientFactory;
    private final BackpressureStrategy backpressureStrategy;

    public Rx2MethodHandlerFactory(PublisherClientFactory publisherClientFactory,
                                   BackpressureStrategy backpressureStrategy) {
        this.publisherClientFactory = publisherClientFactory;
        this.backpressureStrategy = backpressureStrategy;
    }

    @Override
    public MethodHandler create(final Target target, final MethodMetadata metadata) {
        MethodHandler methodHandler = new Rx2PublisherClientMethodHandler(
                target, metadata, publisherClientFactory.apply(metadata),
                backpressureStrategy);

        return new Rx2MethodHandler(methodHandler, returnPublisherType(metadata));
    }

    @Override
    public MethodHandler createDefault(Method method) {
        return new DefaultMethodHandler(method);
    }
}
