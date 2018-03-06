package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.reactive.BuildTemplateByResolvingArgs;
import feign.reactive.ReactiveDelegatingContract;
import feign.reactive.ReactiveOptions;
import feign.reactive.client.ReactiveClientFactory;
import feign.reactive.client.WebReactiveClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static feign.Util.checkNotNull;
import static feign.Util.isDefault;

/**
 * Allows Feign interfaces to return reactive {@link Mono} or {@link Flux}.
 *
 * @author Sergii Karpenko
 */
public class ReactiveFeign {
    private final ParseHandlersByName targetToHandlersByName;
    private final InvocationHandlerFactory factory;

    protected ReactiveFeign(
            final ParseHandlersByName targetToHandlersByName,
            final InvocationHandlerFactory factory) {
        this.targetToHandlersByName = targetToHandlersByName;
        this.factory = factory;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <T> T newInstance(Target<T> target) {
        final Map<String, MethodHandler> nameToHandler =
                targetToHandlersByName.apply(target);
        final Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<>();
        final List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<>();

        for (final Method method : target.type().getMethods()) {
            if (isDefault(method)) {
                final DefaultMethodHandler handler = new DefaultMethodHandler(method);
                defaultMethodHandlers.add(handler);
                methodToHandler.put(method, handler);
            } else {
                methodToHandler.put(method, nameToHandler.get(
                        Feign.configKey(target.type(), method)));
            }
        }

        final InvocationHandler handler = factory.create(target, methodToHandler);
        T proxy = (T) Proxy.newProxyInstance(
                target.type().getClassLoader(),
                new Class<?>[]{target.type()}, handler);

        for (final DefaultMethodHandler defaultMethodHandler :
                defaultMethodHandlers) {
            defaultMethodHandler.bindTo(proxy);
        }

        return proxy;
    }

    /**
     * ReactiveFeign builder.
     */
    public static class Builder {
        private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
        private Contract contract = new ReactiveDelegatingContract(new Contract.Default());
        private WebClient webClient;
        private Encoder encoder = new Encoder.Default();
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        private InvocationHandlerFactory invocationHandlerFactory =
                new ReactiveInvocationHandler.Factory();
        private boolean decode404 = false;

        private feign.reactive.Logger logger = new feign.reactive.Logger();

        public Builder webClient(final WebClient webClient) {
            this.webClient = webClient;
            return this;
        }


        /**
         * Sets contract. Provided contract will be wrapped in
         * {@link ReactiveDelegatingContract}
         *
         * @param contract contract.
         *
         * @return this builder
         */
        public Builder contract(final Contract contract) {
            this.contract = new ReactiveDelegatingContract(contract);
            return this;
        }

        /**
         * Sets encoder.
         *
         * @param encoder encoder
         *
         * @return this builder
         */
        public Builder encoder(final Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        /**
         * This flag indicates that the {@link #decoder(Decoder) decoder} should
         * process responses with 404 status, specifically returning null or empty
         * instead of throwing {@link FeignException}.
         *
         * <p>All first-party (ex gson) decoders return well-known empty values
         * defined by {@link Util#emptyValueOf}. To customize further, wrap an
         * existing {@link #decoder(Decoder) decoder} or make your own.
         *
         * <p>This flag only works with 404, as opposed to all or arbitrary status
         * codes. This was an explicit decision: 404 - empty is safe, common and
         * doesn't complicate redirection, retry or fallback policy.
         *
         * @return this builder
         */
        public Builder decode404() {
            this.decode404 = true;
            return this;
        }

        /**
         * Sets error decoder.
         *
         * @param errorDecoder error deoceder
         *
         * @return this builder
         */
        public Builder errorDecoder(final ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        /**
         * Sets request options using Feign {@link Request.Options}
         *
         * @param options Feign {@code Request.Options} object
         *
         * @return this builder
         */
        public Builder options(final Request.Options options) {
            boolean tryUseCompression = options instanceof ReactiveOptions
                    && ((ReactiveOptions) options).isTryUseCompression();

            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
                    opts -> opts.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.connectTimeoutMillis())
                            .compression(tryUseCompression)
                            .afterNettyContextInit(ctx -> {
                                ctx.addHandlerLast(new ReadTimeoutHandler(options.readTimeoutMillis(), TimeUnit.MILLISECONDS));
                            }));

            this.webClient = webClient.mutate().clientConnector(connector).build();
            return this;
        }


        /**
         * Adds a single request interceptor to the builder.
         *
         * @param requestInterceptor request interceptor to add
         *
         * @return this builder
         */
        public Builder requestInterceptor(
                final RequestInterceptor requestInterceptor) {
            this.requestInterceptors.add(requestInterceptor);
            return this;
        }

        /**
         * Sets the full set of request interceptors for the builder, overwriting
         * any previous interceptors.
         *
         * @param requestInterceptors set of request interceptors
         *
         * @return this builder
         */
        public Builder requestInterceptors(
                final Iterable<RequestInterceptor> requestInterceptors) {
            this.requestInterceptors.clear();
            for (RequestInterceptor requestInterceptor : requestInterceptors) {
                this.requestInterceptors.add(requestInterceptor);
            }
            return this;
        }

        /**
         * Defines target and builds client.
         *
         * @param apiType API interface
         * @param url base URL
         * @param <T> class of API interface
         *
         * @return built client
         */
         public <T> T target(final Class<T> apiType, final String url) {
            return target(new Target.HardCodedTarget<>(apiType, url));
        }

        /**
         * Defines target and builds client.
         *
         * @param target target instance
         * @param <T> class of API interface
         *
         * @return built client
         */
         public <T> T target(final Target<T> target) {
            return build().newInstance(target);
        }

        public ReactiveFeign build() {
            checkNotNull(this.webClient,
                    "WebClient instance wasn't provided in ReactiveFeign builder");



            final ParseHandlersByName handlersByName = new ParseHandlersByName(
                    contract, encoder,
                    new ReactiveMethodHandler.Factory(
                            buildReactiveClientFactory(),
                            requestInterceptors));
            return new ReactiveFeign(handlersByName, invocationHandlerFactory);
        }

        protected ReactiveClientFactory buildReactiveClientFactory() {
            return metadata -> new WebReactiveClient(metadata, webClient, errorDecoder, decode404, logger);
        }

    }

    static final class ParseHandlersByName {
        private final Contract contract;
        private final Encoder encoder;
        private final ReactiveMethodHandler.Factory factory;

        ParseHandlersByName(
                final Contract contract,
                final Encoder encoder,
                final ReactiveMethodHandler.Factory factory) {
            this.contract = contract;
            this.factory = factory;
            this.encoder = checkNotNull(encoder, "encoder must not be null");
        }

        Map<String, MethodHandler> apply(final Target key) {
            final List<MethodMetadata> metadata = contract
                    .parseAndValidatateMetadata(key.type());
            final Map<String, MethodHandler> result = new LinkedHashMap<>();

            for (final MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate;

                if (!md.formParams().isEmpty()
                        && md.template().bodyTemplate() == null) {
                    buildTemplate = new BuildTemplateByResolvingArgs
                            .BuildFormEncodedTemplateFromArgs(md, encoder);
                } else if (md.bodyIndex() != null) {
                    buildTemplate = new BuildTemplateByResolvingArgs
                            .BuildEncodedTemplateFromArgs(md, encoder);
                } else {
                    buildTemplate = new BuildTemplateByResolvingArgs(md);
                }

                ReactiveMethodHandler methodHandler = factory.create(buildTemplate, key, md);
                result.put(md.configKey(), methodHandler);
            }

            return result;
        }
    }
}
