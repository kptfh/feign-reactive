package reactivefeign;

import com.netflix.client.ClientFactory;
import com.netflix.client.RetryHandler;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.Contract;
import feign.InvocationHandlerFactory;
import feign.MethodMetadata;
import feign.Target;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.client.ReactiveClientFactory;
import reactivefeign.client.ReactiveHttpClient;
import reactivefeign.client.ReactiveStatusHandler;
import reactivefeign.client.RibbonReactiveClient;

import java.net.URI;
import java.net.URISyntaxException;
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
        private Function<String, LoadBalancerCommand<Object>> loadBalancerCommandFactory = s -> null;

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

        public Builder<T> enableLoadBalancer(){
            setLoadBalancerCommandFactory(serviceName ->
                    LoadBalancerCommand.builder()
                            .withLoadBalancer(ClientFactory.getNamedLoadBalancer(serviceName))
                            .build());
            return this;
        }

        public Builder<T> enableLoadBalancer(RetryHandler retryHandler){
            setLoadBalancerCommandFactory(serviceName ->
                    LoadBalancerCommand.builder()
                    .withLoadBalancer(ClientFactory.getNamedLoadBalancer(serviceName))
                    .withRetryHandler(retryHandler)
                    .build());
            return this;
        }


        public Builder<T> setLoadBalancerCommandFactory(
                Function<String, LoadBalancerCommand<Object>> loadBalancerCommandFactory) {
            this.loadBalancerCommandFactory = loadBalancerCommandFactory;
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
                String serviceName = extractServiceName(target.url());
                return new RibbonReactiveClient(methodMetadata,
                        loadBalancerCommandFactory.apply(serviceName), reactiveClient);
            };
        }

        private String extractServiceName(String url){
            try {
                return new URI(url).getHost();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Can't extract service name from url", e);
            }
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
        public ReactiveFeign.Builder<T> statusHandler(ReactiveStatusHandler statusHandler) {
            super.statusHandler(statusHandler);
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
