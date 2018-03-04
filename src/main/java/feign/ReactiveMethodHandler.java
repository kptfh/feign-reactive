package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.ErrorDecoder;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static feign.Util.checkNotNull;
import static feign.Util.resolveLastTypeParameter;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Method handler for asynchronous HTTP requests via {@link WebClient}.
 * Inspired by {@link SynchronousMethodHandler}.
 *
 * @author Sergii Karpenko
 */
public class ReactiveMethodHandler
        implements MethodHandler {

    private final MethodMetadata metadata;
    private final String methodTag;
    private final Type returnPublisherType;
    private final ParameterizedTypeReference<?> returnActualType;
    private final Target<?> target;
    private final WebClient client;
    private final List<RequestInterceptor> requestInterceptors;
    private final feign.reactive.Logger logger;
    private final Function<Object[], RequestTemplate> buildTemplateFromArgs;
    private final ErrorDecoder errorDecoder;
    private final boolean decode404;

    private ReactiveMethodHandler(
            Target<?> target,
            WebClient client,
            List<RequestInterceptor> requestInterceptors,
            feign.reactive.Logger logger,
            MethodMetadata metadata,
            Function<Object[], RequestTemplate> buildTemplateFromArgs,
            ErrorDecoder errorDecoder,
            boolean decode404) {
        this.target = checkNotNull(target, "target must be not null");
        this.client = checkNotNull(client, "client must be not null");
        this.requestInterceptors = checkNotNull(requestInterceptors,
                "requestInterceptors for %s must be not null", target);
        this.logger = checkNotNull(logger,
                "logger for %s must be not null", target);
        this.metadata = checkNotNull(metadata,
                "metadata for %s must be not null", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs,
                "metadata for %s must be not null", target);
        this.errorDecoder = checkNotNull(errorDecoder,
                "errorDecoder for %s must be not null", target);
        this.decode404 = decode404;

        this.methodTag = metadata.configKey().substring(0, metadata.configKey().indexOf('('));

        final Type returnType = metadata.returnType();
        returnPublisherType = ((ParameterizedType) returnType).getRawType();
        returnActualType = ParameterizedTypeReference.forType(
                resolveLastTypeParameter(returnType, (Class<?>) returnPublisherType));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) throws Throwable {

        final RequestTemplate template = buildTemplateFromArgs.apply(argv);

        final Request request = targetRequest(template);

        logger.logRequest(methodTag, request);

        long start = System.currentTimeMillis();
        WebClient.ResponseSpec response = client.method(HttpMethod.resolve(request.method()))
                .uri(request.url())
                .headers(httpHeaders -> request.headers().forEach(
                        (key, value) -> httpHeaders.put(key, (List<String>) value)))
                .body(request.body() != null ? BodyInserters.fromObject(request.body()) : BodyInserters.empty())
                .retrieve()
                .onStatus( httpStatus -> decode404 && httpStatus == NOT_FOUND,
                        clientResponse -> null)
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse.bodyToMono(ByteArrayResource.class)
                                .map(ByteArrayResource::getByteArray)
                                .defaultIfEmpty(new byte[0])
                                .map(bodyData -> errorDecoder.decode(metadata.configKey(),
                                        Response.create(
                                                clientResponse.statusCode().value(),
                                                clientResponse.statusCode().getReasonPhrase(),
                                                clientResponse.headers().asHttpHeaders().entrySet().stream()
                                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                                                bodyData)))

                )
                .onStatus(httpStatus -> true, clientResponse -> {
                    logger.logResponseHeaders(methodTag, clientResponse.headers().asHttpHeaders());
                    return null;
                });

        if (returnPublisherType == Mono.class){
            return response.bodyToMono(returnActualType)
                    .map(result -> {
                        logger.logResponse(methodTag, result, System.currentTimeMillis() - start);
                        return result;
                    });

        } else {
            return response.bodyToFlux(returnActualType)
                    .map(result -> {
                        logger.logResponse(methodTag, result, System.currentTimeMillis() - start);
                        return result;
                    });
        }
    }


    /**
     * Associates request to defined target.
     *
     * @param template request template
     *
     * @return fully formed request
     */
    private Request targetRequest(final RequestTemplate template) {
        for (RequestInterceptor interceptor : requestInterceptors) {
            interceptor.apply(template);
        }
        return target.apply(new RequestTemplate(template));
    }

    static class Factory {
        private final WebClient client;
        private final List<RequestInterceptor> requestInterceptors;
        private final feign.reactive.Logger logger;
        private final boolean decode404;

        Factory(
                final WebClient client,
                final List<RequestInterceptor> requestInterceptors,
                final feign.reactive.Logger logger,
                final boolean decode404) {
            this.client = checkNotNull(client, "client must not be null");
            this.requestInterceptors = checkNotNull(requestInterceptors,
                    "requestInterceptors must not be null");
            this.logger = checkNotNull(logger, "logger must not be null");
            this.decode404 = decode404;
        }

        MethodHandler create(
                final Target<?> target,
                final MethodMetadata metadata,
                final Function<Object[], RequestTemplate> buildTemplateFromArgs,
                final ErrorDecoder errorDecoder) {
            return new ReactiveMethodHandler(
                    target,
                    client,
                    requestInterceptors,
                    logger,
                    metadata,
                    buildTemplateFromArgs,
                    errorDecoder,
                    decode404);
        }
    }


}
