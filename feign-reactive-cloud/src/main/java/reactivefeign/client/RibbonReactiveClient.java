package reactivefeign.client;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import feign.MethodMetadata;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Sergii Karpenko
 */
public class RibbonReactiveClient implements ReactiveClient {

    private final LoadBalancerCommand<Object> loadBalancerCommand;
    private final ReactiveClient reactiveClient;
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
    public Publisher<Object> executeRequest(ReactiveRequest request) {

        if (loadBalancerCommand != null) {
            Observable<Object> observable = loadBalancerCommand.submit(server -> {

                ReactiveRequest lbRequest = loadBalanceRequest(request, server);

                return RxReactiveStreams.toObservable(reactiveClient.executeRequest(lbRequest));
            });

            Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

            return returnPublisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
        } else {
            return reactiveClient.executeRequest(request);
        }
    }

    protected ReactiveRequest loadBalanceRequest(ReactiveRequest request, Server server) {
        URI uri = request.uri();
        try {
            URI lbUrl = new URI(uri.getScheme(), uri.getUserInfo(), server.getHost(), server.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
            return new ReactiveRequest(request.method(), lbUrl, request.headers(), request.body());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
