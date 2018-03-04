package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static feign.Util.checkNotNull;

/**
 * {@link InvocationHandler} implementation that transforms calls to methods of
 * feign contract into asynchronous HTTP requests via spring WebClient.
 *
 * @author Sergii Karpenko
 */
final class ReactiveInvocationHandler implements InvocationHandler {
  private final Target<?> target;
  private final Map<Method, MethodHandler> dispatch;

  private ReactiveInvocationHandler(final Target<?> target,
                                    final Map<Method, MethodHandler> dispatch) {
    this.target = checkNotNull(target, "target must not be null");
    this.dispatch = checkNotNull(dispatch, "dispatch must not be null");
  }

  @Override
  public Object invoke(final Object proxy,
                       final Method method,
                       final Object[] args) throws Throwable {
    switch (method.getName()) {
      case "equals" :
        final Object otherHandler = args.length > 0 && args[0] != null
            ? Proxy.getInvocationHandler(args[0])
            : null;
        return equals(otherHandler);
      case "hashCode" :
        return hashCode();
      case "toString":
        return toString();
      default :
        if (isReturnsMonoOrFlux(method)) {
          return invokeRequestMethod(method, args);
        } else {
          throw new FeignException(String.format(
              "Method %s of contract %s doesn't return reactor.core.publisher.Mono or reactor.core.publisher.Flux",
              method.getName(), method.getDeclaringClass().getSimpleName()));
        }
    }
  }

  /**
   * Transforms method invocation into request that executed by
   * {@link org.springframework.web.reactive.function.client.WebClient}.
   *
   * @param method invoked method
   * @param args provided arguments to method
   *
   * @return Publisher with decoded result
   */
  private Publisher invokeRequestMethod(final Method method, final Object[] args) {
    try {
      return (Publisher) dispatch.get(method).invoke(args);
    } catch (Throwable throwable) {
      return Mono.error(throwable);
    }
  }

  /**
   * Checks if method must return {@link Mono} or {@link Flux}.
   *
   * @param method invoked method
   *
   * @return true if method returns {@link Mono} or {@link Flux}, false if not
   */
  private boolean isReturnsMonoOrFlux(final Method method) {
    return Mono.class.isAssignableFrom(method.getReturnType())
            || Flux.class.isAssignableFrom(method.getReturnType()) ;
  }

  @Override
  public boolean equals(final Object other) {
    if (other instanceof ReactiveInvocationHandler) {
      final ReactiveInvocationHandler otherHandler =
          (ReactiveInvocationHandler) other;
      return this.target.equals(otherHandler.target);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return target.hashCode();
  }

  @Override
  public String toString() {
    return target.toString();
  }

  /**
   * Factory for ReactiveInvocationHandler.
   */
  static final class Factory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(final Target target,
                                    final Map<Method, MethodHandler> dispatch) {
      return new ReactiveInvocationHandler(target, dispatch);
    }
  }
}
