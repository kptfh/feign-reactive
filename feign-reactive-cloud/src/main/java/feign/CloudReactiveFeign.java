package feign;

import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.reactive.ReactiveMethodHandlerFactory;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import feign.reactive.client.RibbonReactiveClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Allows to specify ribbon {@link LoadBalancerCommand}
 *  and HystrixObservableCommand.Setter.
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

        private HystrixObservableCommand.Setter hystrixObservableCommandSetter;
        private ReactiveMethodHandlerFactory fallbackMethodHandlerFactory;
        private LoadBalancerCommand<Object> loadBalancerCommand;

        public Builder setHystrixObservableCommandSetter(HystrixObservableCommand.Setter hystrixObservableCommandSetter) {
            this.hystrixObservableCommandSetter = hystrixObservableCommandSetter;
            return this;
        }

        public Builder setFallbackMethodHandlerFactory(ReactiveMethodHandlerFactory fallbackMethodHandlerFactory) {
            this.fallbackMethodHandlerFactory = fallbackMethodHandlerFactory;
            return this;
        }

        public Builder setLoadBalancerCommand(LoadBalancerCommand<Object> loadBalancerCommand) {
            this.loadBalancerCommand = loadBalancerCommand;
            return this;
        }

        @Override
        protected ReactiveMethodHandlerFactory buildReactiveMethodHandlerFactory() {
            ReactiveMethodHandlerFactory reactiveMethodHandlerFactory = super.buildReactiveMethodHandlerFactory();
            return new HystrixMethodHandler.Factory(
                    reactiveMethodHandlerFactory,
                    fallbackMethodHandlerFactory,
                    hystrixObservableCommandSetter
            );
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

    }
}
