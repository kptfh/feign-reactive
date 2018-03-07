package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.reactive.*;
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

    public static <T> Builder<T> builder() {
        return new Builder<>();
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
    public static class Builder<T> {
        private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
        private Contract contract = new ReactiveDelegatingContract(new Contract.Default());
        private WebClient webClient;
        private Encoder encoder = new Encoder.Default();
        private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        private InvocationHandlerFactory invocationHandlerFactory =
                new ReactiveInvocationHandler.Factory();
        private boolean decode404 = false;

        private feign.reactive.Logger logger = new feign.reactive.Logger();

        public Builder<T> webClient(final WebClient webClient) {
            this.webClient = webClient;
            return this;
        }


        /**
         * Sets contract. Provided contract will be wrapped in
         * {@link ReactiveDelegatingContract}
         *
         * @param contract contract.
         * @return this builder
         */
        public Builder<T> contract(final Contract contract) {
            this.contract = new ReactiveDelegatingContract(contract);
            return this;
        }

        /**
         * Sets encoder.
         *
         * @param encoder encoder
         * @return this builder
         */
        public Builder<T> encoder(final Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        /**
         * This flag indicates that the {@link #decoder(Decoder) decoder} should
         * process responses with 404 status, specifically returning null or empty
         * instead of throwing {@link FeignException}.
         * <p>
         * <p>All first-party (ex gson) decoders return well-known empty values
         * defined by {@link Util#emptyValueOf}. To customize further, wrap an
         * existing {@link #decoder(Decoder) decoder} or make your own.
         * <p>
         * <p>This flag only works with 404, as opposed to all or arbitrary status
         * codes. This was an explicit decision: 404 - empty is safe, common and
         * doesn't complicate redirection, retry or fallback policy.
         *
         * @return this builder
         */
        public Builder<T> decode404() {
            this.decode404 = true;
            return this;
        }

        /**
         * Sets error decoder.
         *
         * @param errorDecoder error deoceder
         * @return this builder
         */
        public Builder<T> errorDecoder(final ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        /**
         * Sets request options using Feign {@link Request.Options}
         *
         * @param options Feign {@code Request.Options} object
         * @return this builder
         */
        public Builder<T> options(final Request.Options options) {
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
         * Defines target and builds client.
         *
         * @param apiType API interface
         * @param url     base URL
         * @return built client
         */
        public T target(final Class<T> apiType, final String url) {
            return target(new Target.HardCodedTarget<>(apiType, url));
        }

        /**
         * Defines target and builds client.
         *
         * @param target target instance
         * @return built client
         */
        public T target(final Target<T> target) {
            return build().newInstance(target);
        }

        public ReactiveFeign build() {
            checkNotNull(this.webClient,
                    "WebClient instance wasn't provided in ReactiveFeign builder");

            final ParseHandlersByName handlersByName = new ParseHandlersByName(
                    contract,
                    buildReactiveMethodHandlerFactory());
            return new ReactiveFeign(handlersByName, invocationHandlerFactory);
        }

        protected ReactiveMethodHandlerFactory buildReactiveMethodHandlerFactory() {
            return new ReactiveClientMethodHandler.Factory(
                    encoder, buildReactiveClientFactory());
        }

        protected ReactiveClientFactory buildReactiveClientFactory() {
            return metadata -> new WebReactiveClient(metadata, webClient, errorDecoder, decode404, logger);
        }

    }

    static final class ParseHandlersByName {
        private final Contract contract;
        private final ReactiveMethodHandlerFactory factory;

        ParseHandlersByName(
                final Contract contract,
                final ReactiveMethodHandlerFactory factory) {
            this.contract = contract;
            this.factory = factory;
        }

        Map<String, MethodHandler> apply(final Target target) {
            final List<MethodMetadata> metadata = contract
                    .parseAndValidatateMetadata(target.type());
            final Map<String, MethodHandler> result = new LinkedHashMap<>();

            for (final MethodMetadata md : metadata) {
                ReactiveMethodHandler methodHandler = factory.create(target, md);
                result.put(md.configKey(), methodHandler);
            }

            return result;
        }
    }
}
