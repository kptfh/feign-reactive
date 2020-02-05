package reactivefeign.client;

import reactor.core.publisher.Mono;

import java.util.function.Function;


public interface ReactiveHttpExchangeFilterFunction {

    Mono<ReactiveHttpResponse> filter(
            ReactiveHttpRequest request,
            ReactiveHttpClient exchangeFunction);

    default ReactiveHttpExchangeFilterFunction then(
            ReactiveHttpExchangeFilterFunction afterFilter) {
        return (request, next) -> filter(request, afterRequest -> afterFilter.filter(afterRequest, next));
    }

    default ReactiveHttpClient filter(ReactiveHttpClient exchangeFunction){
        return request -> filter(request, exchangeFunction);
    }

    static ReactiveHttpExchangeFilterFunction ofRequestProcessor(
            Function<ReactiveHttpRequest, Mono<ReactiveHttpRequest>> processor) {
        return (request, next) -> processor.apply(request).flatMap(next::executeRequest);
    }

    static ReactiveHttpExchangeFilterFunction ofResponseProcessor(
            Function<ReactiveHttpResponse, Mono<ReactiveHttpResponse>> processor) {
        return (request, next) -> next.executeRequest(request).flatMap(processor);
    }

}
