package reactivefeign.methodhandler;

import feign.InvocationHandlerFactory;

public interface MethodHandler extends InvocationHandlerFactory.MethodHandler{

	void bindTo(Object proxy);

}
