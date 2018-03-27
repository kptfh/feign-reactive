package reactivefeign;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.*;
import feign.codec.ErrorDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.client.ReactiveClientFactory;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.RibbonReactiveClient;

import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * Allows to specify ribbon {@link LoadBalancerCommand}
 * and HystrixObservableCommand.Setter with fallback factory.
 *
 * @author Sergii Karpenko
 */
public class CloudReactiveFeign extends ReactiveFeign {

    private CloudReactiveFeign(ReactiveFeign.ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory) {
        super(targetToHandlersByName, factory);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> extends ReactiveFeign.Builder<T> {

        private SetterFactory commandSetterFactory;
        private Function<Throwable, ? extends T> fallbackFactory;
        private LoadBalancerCommand<Object> loadBalancerCommand;

        public Builder<T> setHystrixCommandSetterFactory(SetterFactory commandSetterFactory) {
            this.commandSetterFactory = commandSetterFactory;
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
            return commandSetterFactory != null || fallbackFactory != null
                    ? new HystrixMethodHandler.Factory(
                    reactiveMethodHandlerFactory,
                    ofNullable(commandSetterFactory).orElse(new DefaultSetterFactory()),
                    (Function<Throwable, Object>) fallbackFactory)
                    : reactiveMethodHandlerFactory;
        }

        @Override
        protected ReactiveClientFactory buildReactiveClientFactory() {
            ReactiveClientFactory reactiveClientFactory = super.buildReactiveClientFactory();

            return methodMetadata -> {
                ReactiveHttpClient reactiveClient = reactiveClientFactory.apply(methodMetadata);

                return new RibbonReactiveClient(methodMetadata, loadBalancerCommand, reactiveClient);
            };
        }

        @Override
        public Builder<T> webClient(final WebClient webClient) {
            super.webClient(webClient);
            return this;
        }

        @Override
        public Builder<T> contract(final Contract contract) {
            super.contract(contract);
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
        public Builder<T> options(final ReactiveOptions options) {
            super.options(options);
            return this;
        }
    }

    public interface SetterFactory {
        HystrixObservableCommand.Setter create(Target<?> target, MethodMetadata methodMetadata);
    }

    public static class DefaultSetterFactory implements SetterFactory {
        @Override
        public HystrixObservableCommand.Setter create(Target<?> target, MethodMetadata methodMetadata) {
            String groupKey = target.name();
            String commandKey = methodMetadata.configKey();
            return HystrixObservableCommand.Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
        }
    }

}
