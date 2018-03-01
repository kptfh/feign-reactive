package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.ErrorDecoder;
import feign.reactive.ReactiveRetryer;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static feign.Util.checkNotNull;
import static feign.Util.resolveLastTypeParameter;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static reactor.core.publisher.Mono.just;

/**
 * Method handler for asynchronous HTTP requests via {@link WebClient}.
 * Inspired by {@link SynchronousMethodHandler}.
 *
 * @author Sergii Karpenko
 */
class ReactiveMethodHandler
        implements MethodHandler {

    private final MethodMetadata metadata;
    private final Type returnPublisherType;
    private final ParameterizedTypeReference<?> returnActualType;
    private final Target<?> target;
    private final WebClient client;
    private final ReactiveRetryer retryer;
    private final List<RequestInterceptor> requestInterceptors;
    private final Logger logger;
    private final Logger.Level logLevel;
    private final Function<Object[], RequestTemplate> buildTemplateFromArgs;
    private final ErrorDecoder errorDecoder;
    private final boolean decode404;

    private ReactiveMethodHandler(
            Target<?> target,
            WebClient client,
            ReactiveRetryer retryer,
            List<RequestInterceptor> requestInterceptors,
            Logger logger,
            Logger.Level logLevel,
            MethodMetadata metadata,
            Function<Object[], RequestTemplate> buildTemplateFromArgs,
            ErrorDecoder errorDecoder,
            boolean decode404) {
        this.target = checkNotNull(target, "target must be not null");
        this.client = checkNotNull(client, "client must be not null");
        this.retryer = checkNotNull(retryer, "retryer for %s must be not null", target);;
        this.requestInterceptors = checkNotNull(requestInterceptors,
                "requestInterceptors for %s must be not null", target);
        this.logger = checkNotNull(logger,
                "logger for %s must be not null", target);
        this.logLevel = checkNotNull(logLevel,
                "logLevel for %s must be not null", target);
        this.metadata = checkNotNull(metadata,
                "metadata for %s must be not null", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs,
                "metadata for %s must be not null", target);
        this.errorDecoder = checkNotNull(errorDecoder,
                "errorDecoder for %s must be not null", target);
        this.decode404 = decode404;

        final Type returnType = metadata.returnType();
        returnPublisherType = ((ParameterizedType) returnType).getRawType();
        returnActualType = ParameterizedTypeReference.forType(
                resolveLastTypeParameter(returnType, (Class<?>) returnPublisherType));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) throws Throwable {
        final RequestTemplate template = buildTemplateFromArgs.apply(argv);
        return executeAndDecode(template);
    }

    /**
     * Executes request from {@code template} with {@code this.client} and
     * decodes the response. Result or occurred error wrapped in returned Future.
     *
     * @param template request template
     * @return future with decoded result or occurred error
     */
    private Publisher executeAndDecode(final RequestTemplate template) {
        final Request request = targetRequest(template);
        logRequest(request);

        WebClient.ResponseSpec response = client.method(HttpMethod.resolve(request.method()))
                .uri(request.url())
                .headers(httpHeaders -> request.headers().forEach(
                        (key, value) -> httpHeaders.put(key, (List<String>) value)))
                .body(request.body() != null ? BodyInserters.fromObject(request.body()) : BodyInserters.empty())
                .retrieve()
                .onStatus( httpStatus -> decode404 && httpStatus == NOT_FOUND,
                        clientResponse -> null)
                .onStatus(HttpStatus::isError,
                        clientResponse -> just(errorDecoder.decode(metadata.configKey(),
                                Response.create(
                                        clientResponse.statusCode().value(),
                                        clientResponse.statusCode().getReasonPhrase(),
                                        clientResponse.headers().asHttpHeaders().entrySet().stream()
                                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                                        extractBodyContent(clientResponse)))));

        if (returnPublisherType == Mono.class){
            return response.bodyToMono(returnActualType).retryWhen(whenFactory());

        } else {
            return response.bodyToFlux(returnActualType).retryWhen(whenFactory());
        }
    }

    protected Function<Flux<Throwable>, Publisher<?>> whenFactory() {
        final ReactiveRetryer retryerFinal = retryer.clone();
        return companion ->
                companion.flatMap(error -> {
                    long delay = retryerFinal.doRetryIn(error);
                    if(delay >= 0){
                        logRetry();
                        return Mono.delay(Duration.ofMillis(delay));
                    } else {
                        throw Exceptions.propagate(error);
                    }
                });
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

    /**
     * Logs request.
     *
     * @param request HTTP request
     */
    private void logRequest(final Request request) {
        if (logLevel != Logger.Level.NONE) {
            logger.logRequest(metadata.configKey(), logLevel, request);
        }
    }

    /**
     * Logs retry.
     */
    private void logRetry() {
        if (logLevel != Logger.Level.NONE) {
            logger.logRetry(metadata.configKey(), logLevel);
        }
    }

    private static byte[] extractBodyContent(ClientResponse clientResponse) {
        try {
            return clientResponse.bodyToMono(ByteArrayResource.class)
                    .blockOptional(Duration.ZERO)
                    .map(ByteArrayResource::getByteArray).orElse(null);
        } catch (Throwable e){
            return null;
        }
    }

    static class Factory {
        private final WebClient client;
        private final ReactiveRetryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean decode404;

        Factory(
                final WebClient client,
                final ReactiveRetryer retryer,
                final List<RequestInterceptor> requestInterceptors,
                final Logger logger,
                final Logger.Level logLevel,
                final boolean decode404) {
            this.client = checkNotNull(client, "client must not be null");
            this.retryer = checkNotNull(retryer, "retryer must not be null");
            this.requestInterceptors = checkNotNull(requestInterceptors,
                    "requestInterceptors must not be null");
            this.logger = checkNotNull(logger, "logger must not be null");
            this.logLevel = checkNotNull(logLevel, "logLevel must not be null");
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
                    retryer,
                    requestInterceptors,
                    logger,
                    logLevel,
                    metadata,
                    buildTemplateFromArgs,
                    errorDecoder,
                    decode404);
        }
    }


}
