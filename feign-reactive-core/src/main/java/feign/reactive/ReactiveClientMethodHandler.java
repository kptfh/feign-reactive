package feign.reactive;

import feign.*;
import feign.codec.Encoder;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Function;

import static feign.Util.checkNotNull;

/**
 * Method handler for asynchronous HTTP requests via {@link WebClient}.
 *
 * @author Sergii Karpenko
 */
public class ReactiveClientMethodHandler implements ReactiveMethodHandler {

    private final Target target;
    private final Function<Object[], RequestTemplate> buildTemplateFromArgs;
    private final ReactiveClient reactiveClient;

    private ReactiveClientMethodHandler(
            Target target, Function<Object[], RequestTemplate> buildTemplateFromArgs,
            ReactiveClient reactiveClient) {
        this.target = checkNotNull(target, "target must be not null");
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "buildTemplateFromArgs must be not null");
        this.reactiveClient = checkNotNull(reactiveClient, "client must be not null");

    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) {

        final RequestTemplate template = buildTemplateFromArgs.apply(argv);

        //associates request to defined target.
        final Request request = target.apply(new RequestTemplate(template));

        return reactiveClient.executeRequest(request);
    }

    public static class Factory implements ReactiveMethodHandlerFactory {
        private final Encoder encoder;
        private final ReactiveClientFactory reactiveClientFactory;

        public Factory(Encoder encoder, final ReactiveClientFactory reactiveClientFactory) {
            this.encoder = checkNotNull(encoder, "encoder must not be null");
            this.reactiveClientFactory = checkNotNull(reactiveClientFactory, "client must not be null");
        }

        @Override
        public ReactiveClientMethodHandler create(Target target, final MethodMetadata metadata) {

            BuildTemplateByResolvingArgs buildTemplate;
            if (!metadata.formParams().isEmpty()
                    && metadata.template().bodyTemplate() == null) {
                buildTemplate = new BuildTemplateByResolvingArgs
                        .BuildFormEncodedTemplateFromArgs(metadata, encoder);
            } else if (metadata.bodyIndex() != null) {
                buildTemplate = new BuildTemplateByResolvingArgs
                        .BuildEncodedTemplateFromArgs(metadata, encoder);
            } else {
                buildTemplate = new BuildTemplateByResolvingArgs(metadata);
            }

            return new ReactiveClientMethodHandler(
                    target,
                    buildTemplate,
                    reactiveClientFactory.apply(metadata));
        }
    }
}
