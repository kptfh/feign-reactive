package feign;

import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import feign.reactive.client.RibbonReactiveClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Allows to specify ribbon {@link LoadBalancerCommand}.
 *
 * @author Sergii Karpenko
 */
public class CloudReactiveFeign extends ReactiveFeign{

    private CloudReactiveFeign(ReactiveFeign.ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory) {
        super(targetToHandlersByName, factory);
    }

    public static CloudReactiveFeign.Builder builder() {
        return new CloudReactiveFeign.Builder();
    }

    public static class Builder extends ReactiveFeign.Builder {

        private LoadBalancerCommand<Object> loadBalancerCommand;

        public Builder setLoadBalancerCommand(LoadBalancerCommand<Object> loadBalancerCommand) {
            this.loadBalancerCommand = loadBalancerCommand;
            return this;
        }

        @Override
        protected ReactiveClientFactory buildReactiveClientFactory() {
            ReactiveClientFactory reactiveClientFactory = super.buildReactiveClientFactory();

            return methodMetadata -> {
                ReactiveClient reactiveClient = reactiveClientFactory.apply(methodMetadata);

                return new RibbonReactiveClient(methodMetadata, loadBalancerCommand, reactiveClient);
            };
        }

        @Override
        public Builder webClient(final WebClient webClient){
            super.webClient(webClient);
            return this;
        }

        @Override
        public Builder contract(final Contract contract) {
            super.contract(contract);
            return this;
        }

        @Override
        public Builder encoder(final Encoder encoder) {
            super.encoder(encoder);
            return this;
        }

        @Override
        public Builder decode404() {
            super.decode404();
            return this;
        }

        @Override
        public Builder errorDecoder(final ErrorDecoder errorDecoder) {
            super.errorDecoder(errorDecoder);
            return this;
        }

        @Override
        public Builder options(final Request.Options options) {
            super.options(options);
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
            super.requestInterceptor(requestInterceptor);
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
            super.requestInterceptors(requestInterceptors);
            return this;
        }
    }
}
