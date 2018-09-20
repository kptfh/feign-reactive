package reactivefeign.webclient.client;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import org.reactivestreams.Publisher;
import org.springframework.lang.Nullable;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.publisher.PublisherHttpClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Sergii Karpenko
 */
public class RibbonPublisherClient implements PublisherHttpClient {

    private final LoadBalancerCommand<Object> loadBalancerCommand;
    private final PublisherHttpClient publisherClient;

    public RibbonPublisherClient(@Nullable LoadBalancerCommand<Object> loadBalancerCommand,
                                 PublisherHttpClient publisherClient) {
        this.loadBalancerCommand = loadBalancerCommand;
        this.publisherClient = publisherClient;
    }

    @Override
    public Publisher<Object> executeRequest(ReactiveHttpRequest request, Type publisherType) {

        if (loadBalancerCommand != null) {
            Observable<Object> observable = loadBalancerCommand.submit(server -> {

                ReactiveHttpRequest lbRequest = loadBalanceRequest(request, server);

                return RxReactiveStreams.toObservable(publisherClient.executeRequest(lbRequest, publisherType));
            });

            Publisher<Object> publisher = RxReactiveStreams.toPublisher(observable);

            return publisherType == Mono.class ? Mono.from(publisher) : Flux.from(publisher);
        } else {
            return publisherClient.executeRequest(request, publisherType);
        }
    }

    protected ReactiveHttpRequest loadBalanceRequest(ReactiveHttpRequest request, Server server) {
        URI uri = request.uri();
        try {
            URI lbUrl = new URI(uri.getScheme(), uri.getUserInfo(), server.getHost(), server.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment());
            return new ReactiveHttpRequest(request.method(), lbUrl, request.headers(), request.body());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
