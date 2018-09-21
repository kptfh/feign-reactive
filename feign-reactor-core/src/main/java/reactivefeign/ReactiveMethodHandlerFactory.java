package reactivefeign;

import feign.InvocationHandlerFactory;
import feign.MethodMetadata;
import feign.Target;
import reactivefeign.methodhandler.PublisherClientMethodHandler;
import reactivefeign.methodhandler.reactor.ReactorMethodHandler;
import reactivefeign.publisher.PublisherClientFactory;

import java.lang.reflect.Method;

import static feign.Util.checkNotNull;
import static feign.Util.isDefault;
import static reactivefeign.utils.FeignUtils.returnPublisherType;

public class ReactiveMethodHandlerFactory implements MethodHandlerFactory{

	private final PublisherClientFactory publisherClientFactory;

	public ReactiveMethodHandlerFactory(final PublisherClientFactory publisherClientFactory) {
		this.publisherClientFactory = checkNotNull(publisherClientFactory, "client must not be null");
	}

	@Override
	public InvocationHandlerFactory.MethodHandler create(
			Target target, final MethodMetadata metadata, Method method) {

		InvocationHandlerFactory.MethodHandler methodHandler = isDefault(method)
				?

		return new ReactorMethodHandler(
				new PublisherClientMethodHandler(target, metadata,
						publisherClientFactory.apply(metadata)),
				returnPublisherType(metadata));
	}
}
