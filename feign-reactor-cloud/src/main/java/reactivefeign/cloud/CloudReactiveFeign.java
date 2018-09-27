package reactivefeign.cloud;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.ReactiveFeign;
import reactivefeign.ReactiveOptions;
import reactivefeign.ReactiveRetryPolicy;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactivefeign.client.ReactiveHttpResponse;
import reactivefeign.client.statushandler.ReactiveStatusHandler;
import reactivefeign.cloud.methodhandler.HystrixMethodHandlerFactory;
import reactivefeign.cloud.publisher.RibbonPublisherClient;
import reactivefeign.methodhandler.MethodHandlerFactory;
import reactivefeign.publisher.PublisherClientFactory;
import reactivefeign.publisher.PublisherHttpClient;
import reactivefeign.webclient.WebReactiveFeign;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static reactivefeign.utils.FeignUtils.returnPublisherType;

/**
 * Allows to specify ribbon {@link LoadBalancerCommand}
 * and HystrixObservableCommand.Setter with fallback factory.
 *
 * @author Sergii Karpenko
 */
public class CloudReactiveFeign extends ReactiveFeign {

    private static final Logger logger = LoggerFactory.getLogger(CloudReactiveFeign.class);

    private CloudReactiveFeign(ReactiveFeign.ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory) {
        super(targetToHandlersByName, factory);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> Builder<T> builder(WebClient webClient) {
        return new Builder<>(webClient);
    }

    public static class Builder<T> extends WebReactiveFeign.Builder<T> {

        private boolean hystrixEnabled = true;
        private SetterFactory commandSetterFactory = new DefaultSetterFactory();
        private Function<Throwable, ? extends T> fallbackFactory;
        private Function<String, LoadBalancerCommand<Object>> loadBalancerCommandFactory = s -> null;

        protected Builder() {
            super();
        }

        protected Builder(WebClient webClient) {
            super(webClient);
        }

        public void disableHystrix() {
            this.hystrixEnabled = false;
        }

        public Builder<T> setHystrixCommandSetterFactory(SetterFactory commandSetterFactory) {
            this.commandSetterFactory = commandSetterFactory;
            return this;
        }

        public Builder<T> setFallback(T fallback) {
            return setFallbackFactory(throwable -> fallback);
        }

        public Builder<T> setFallbackFactory(Function<Throwable, ? extends T> fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
            return this;
        }

        public Builder<T> enableLoadBalancer(){
            return setLoadBalancerCommandFactory(serviceName ->
                    LoadBalancerCommand.builder()
                            .withLoadBalancer(ClientFactory.getNamedLoadBalancer(serviceName))
                            .build());
        }

        public Builder<T> enableLoadBalancer(RetryHandler retryHandler){
            if(retryHandler.getMaxRetriesOnSameServer() > 0){
                logger.warn("Use retryWhen(ReactiveRetryPolicy retryPolicy) " +
                        "as it allow to configure retry delays (backoff)");
            }
            return setLoadBalancerCommandFactory(serviceName ->
                    LoadBalancerCommand.builder()
                    .withLoadBalancer(ClientFactory.getNamedLoadBalancer(serviceName))
                    .withRetryHandler(retryHandler)
                    .build());
        }


        public Builder<T> setLoadBalancerCommandFactory(
                Function<String, LoadBalancerCommand<Object>> loadBalancerCommandFactory) {
            this.loadBalancerCommandFactory = loadBalancerCommandFactory;
            return this;
        }

        @Override
        protected MethodHandlerFactory buildReactiveMethodHandlerFactory() {
            MethodHandlerFactory methodHandlerFactory = super.buildReactiveMethodHandlerFactory();
            return hystrixEnabled
                    ? new HystrixMethodHandlerFactory(
					methodHandlerFactory,
                    commandSetterFactory,
                    (Function<Throwable, Object>) fallbackFactory)
                    : methodHandlerFactory;
        }

        @Override
        protected PublisherClientFactory buildReactiveClientFactory() {
            PublisherClientFactory publisherClientFactory = super.buildReactiveClientFactory();
            return methodMetadata -> {
                PublisherHttpClient publisherClient = publisherClientFactory.apply(methodMetadata);
                String serviceName = extractServiceName(target.url());
                return new RibbonPublisherClient(loadBalancerCommandFactory.apply(serviceName),
                        publisherClient, returnPublisherType(methodMetadata));
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
        public Builder<T> contract(final Contract contract) {
            super.contract(contract);
            return this;
        }

        @Override
        public Builder<T> requestInterceptor(ReactiveHttpRequestInterceptor requestInterceptor) {
            super.requestInterceptor(requestInterceptor);
            return this;
        }

        @Override
        public Builder<T> decode404() {
            super.decode404();
            return this;
        }

        @Override
        public Builder<T> statusHandler(ReactiveStatusHandler statusHandler) {
            super.statusHandler(statusHandler);
            return this;
        }

        @Override
        public ReactiveFeign.Builder<T> responseMapper(
                BiFunction<MethodMetadata, ReactiveHttpResponse, ReactiveHttpResponse> responseMapper) {
            super.responseMapper(responseMapper);
            return this;
        }

        @Override
        public Builder<T> retryWhen(ReactiveRetryPolicy retryPolicy){
            super.retryWhen(retryPolicy);
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
