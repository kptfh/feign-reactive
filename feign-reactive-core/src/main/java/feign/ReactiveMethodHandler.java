package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.function.Function;

import static feign.Util.checkNotNull;

/**
 * Method handler for asynchronous HTTP requests via {@link WebClient}.
 * Inspired by {@link SynchronousMethodHandler}.
 *
 * @author Sergii Karpenko
 */
public class ReactiveMethodHandler
        implements MethodHandler {

    private final ReactiveClient reactiveClient;

    private final Target<?> target;
    private final List<RequestInterceptor> requestInterceptors;
    private final Function<Object[], RequestTemplate> buildTemplateFromArgs;

    private ReactiveMethodHandler(
            Function<Object[], RequestTemplate> buildTemplateFromArgs,
            Target<?> target,
            List<RequestInterceptor> requestInterceptors,
            ReactiveClient reactiveClient) {
        this.target = checkNotNull(target, "target must be not null");
        this.reactiveClient = checkNotNull(reactiveClient, "client must be not null");
        this.requestInterceptors = checkNotNull(requestInterceptors,
                "requestInterceptors for %s must be not null", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs,
                "metadata for %s must be not null", target);

    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) throws Throwable {

        final RequestTemplate template = buildTemplateFromArgs.apply(argv);

        final Request request = targetRequest(template);

        return reactiveClient.executeRequest(request);
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
        private final List<RequestInterceptor> requestInterceptors;
        private final ReactiveClientFactory reactiveClientFactory;

        Factory(
                final ReactiveClientFactory reactiveClientFactory,
                final List<RequestInterceptor> requestInterceptors) {
            this.reactiveClientFactory = checkNotNull(reactiveClientFactory, "client must not be null");
            this.requestInterceptors = checkNotNull(requestInterceptors,
                    "requestInterceptors must not be null");
        }

        ReactiveMethodHandler create(
                final Function<Object[], RequestTemplate> buildTemplateFromArgs,
                final Target<?> target,
                final MethodMetadata metadata) {
            return new ReactiveMethodHandler(
                    buildTemplateFromArgs,
                    target,
                    requestInterceptors,
                    reactiveClientFactory.apply(metadata));
        }
    }


}
