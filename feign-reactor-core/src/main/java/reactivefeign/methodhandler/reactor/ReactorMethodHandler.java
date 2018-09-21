package reactivefeign.methodhandler.reactor;

import feign.InvocationHandlerFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;

public class ReactorMethodHandler implements InvocationHandlerFactory.MethodHandler {

	private final InvocationHandlerFactory.MethodHandler methodHandler;
	private final Type returnPublisherType;

	public ReactorMethodHandler(InvocationHandlerFactory.MethodHandler methodHandler, Type returnPublisherType) {
		this.methodHandler = methodHandler;
		this.returnPublisherType = returnPublisherType;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Publisher<Object> invoke(final Object[] argv) {
		try {
			return (Publisher<Object>)methodHandler.invoke(argv);
		} catch (Throwable throwable) {
			return returnPublisherType == Mono.class
					? Mono.error(throwable)
					: Flux.error(throwable);
		}
	}
}
