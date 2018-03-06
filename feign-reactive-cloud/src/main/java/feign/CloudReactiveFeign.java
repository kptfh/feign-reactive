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

import java.util.function.Function;

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

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> extends ReactiveFeign.Builder<T> {

        private HystrixObservableCommand.Setter hystrixObservableCommandSetter;
        private Function<Throwable, ? extends T> fallbackFactory;
        private LoadBalancerCommand<Object> loadBalancerCommand;

        public Builder<T> setHystrixObservableCommandSetter(HystrixObservableCommand.Setter hystrixObservableCommandSetter) {
            this.hystrixObservableCommandSetter = hystrixObservableCommandSetter;
            return this;
        }

        public Builder<T> setFallback(T fallback) {
            setFallbackFactory(throwable -> fallback);
            return this;
        }

        public Builder<T> setFallbackFactory(Function<Throwable, ? extends T> fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
            return this;
        }

        public Builder<T> setLoadBalancerCommand(LoadBalancerCommand<Object> loadBalancerCommand) {
            this.loadBalancerCommand = loadBalancerCommand;
            return this;
        }

        @Override
        protected ReactiveMethodHandlerFactory buildReactiveMethodHandlerFactory() {
            ReactiveMethodHandlerFactory reactiveMethodHandlerFactory = super.buildReactiveMethodHandlerFactory();
            return hystrixObservableCommandSetter != null
                    ? new HystrixMethodHandler.Factory(
                    reactiveMethodHandlerFactory,
                    (Function<Throwable, Object>) fallbackFactory,
                    hystrixObservableCommandSetter)
                    : reactiveMethodHandlerFactory;
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
        public Builder<T> webClient(final WebClient webClient){
            super.webClient(webClient);
            return this;
        }

        @Override
        public Builder<T> contract(final Contract contract) {
            super.contract(contract);
            return this;
        }

        @Override
        public Builder<T> encoder(final Encoder encoder) {
            super.encoder(encoder);
            return this;
        }

        @Override
        public Builder<T> decode404() {
            super.decode404();
            return this;
        }

        @Override
        public Builder<T> errorDecoder(final ErrorDecoder errorDecoder) {
            super.errorDecoder(errorDecoder);
            return this;
        }

        @Override
        public Builder<T> options(final Request.Options options) {
            super.options(options);
            return this;
        }

    }
}
