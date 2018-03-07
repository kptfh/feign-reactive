package feign.reactive.client;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.MethodMetadata;
import feign.Request;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;

/**
 * @author Sergii Karpenko
 */
public class RibbonReactiveClient implements ReactiveClient {

    private LoadBalancerCommand<Object> loadBalancerCommand;
    private ReactiveClient reactiveClient;
    private final Type returnPublisherType;

    public RibbonReactiveClient(MethodMetadata metadata,
                                @Nullable
                                        LoadBalancerCommand<Object> loadBalancerCommand,
                                ReactiveClient reactiveClient) {
        this.loadBalancerCommand = loadBalancerCommand;
        this.reactiveClient = reactiveClient;

        returnPublisherType = ((ParameterizedType) metadata.returnType()).getRawType();
    }

    @Override
    public Publisher<Object> executeRequest(Request request) {

        if (loadBalancerCommand != null) {
            Observable<Object> observable = loadBalancerCommand.submit(server -> {

                Request lbRequest = loadBalanceRequest(request, server);

                return RxReactiveStreams.toObservable(reactiveClient.executeRequest(lbRequest));
            });

            Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

            return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
        } else {
            return reactiveClient.executeRequest(request);
        }
    }

    Request loadBalanceRequest(Request request, Server server) {
        URI asUri = URI.create(request.url());
        String clientName = asUri.getHost();

        String lbUrl = request.url().replaceFirst(clientName, server.getHostPort());

        return Request.create(request.method(), lbUrl, request.headers(), request.body(), request.charset());
    }
}
