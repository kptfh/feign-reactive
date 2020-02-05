package reactivefeign.client;

import reactivefeign.utils.Pair;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static reactivefeign.utils.MultiValueMapUtils.addOrdered;

public final class ReactiveHttpRequestInterceptors {

    private ReactiveHttpRequestInterceptors(){}

    public static ReactiveHttpRequestInterceptor addHeader(String header, String value){
        return addHeaders(Collections.singletonList(new Pair<>(header, value)));
    }

    public static ReactiveHttpRequestInterceptor addHeaders(List<Pair<String, String>> headers){
        return from(request -> {
            headers.forEach(header -> addOrdered(request.headers(), header.left, header.right));
            return request;
        });
    }

    public static ReactiveHttpRequestInterceptor from(Function<ReactiveHttpRequest, ReactiveHttpRequest> function){
        return request -> Mono.just(function.apply(request));
    }
}
