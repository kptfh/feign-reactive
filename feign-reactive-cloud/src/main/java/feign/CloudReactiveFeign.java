package feign;

import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import feign.reactive.client.RibbonReactiveClient;

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
    }
}
