package reactivefeign.client;

import java.util.function.Function;

public interface ReactiveHttpRequestInterceptor extends Function<ReactiveHttpRequest, ReactiveHttpRequest> {

}
