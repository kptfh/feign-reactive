package feign.reactive;

import feign.MethodMetadata;
import feign.Target;
import feign.reactive.client.ReactiveClient;
import feign.reactive.client.ReactiveClientFactory;
import feign.reactive.client.ReactiveRequest;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static feign.Util.checkNotNull;
import static java.util.stream.Collectors.*;

/**
 * Method handler for asynchronous HTTP requests via {@link WebClient}.
 *
 * @author Sergii Karpenko
 */
public class ReactiveClientMethodHandler implements ReactiveMethodHandler {

    private final Target target;
    private final MethodMetadata methodMetadata;
    private final ReactiveClient reactiveClient;
    private final DefaultUriBuilderFactory defaultUriBuilderFactory;
    private final Map<String, List<Function<Map<String, ?>, String>>> headerExpanders;

    private ReactiveClientMethodHandler(
            Target target, MethodMetadata methodMetadata,
            ReactiveClient reactiveClient) {
        this.target = checkNotNull(target, "target must be not null");
        this.methodMetadata = checkNotNull(methodMetadata, "methodMetadata must be not null");
        this.reactiveClient = checkNotNull(reactiveClient, "client must be not null");
        this.defaultUriBuilderFactory = new DefaultUriBuilderFactory(target.url());
        Stream<AbstractMap.SimpleImmutableEntry<String, String>> simpleImmutableEntryStream = methodMetadata.template().headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(v -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), v)));
        this.headerExpanders = simpleImmutableEntryStream
                .collect(groupingBy(entry -> entry.getKey(),
                        mapping(entry -> buildExpandHeaderFunction(entry.getValue()), toList())));

    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher invoke(final Object[] argv) {

        final ReactiveRequest request = buildRequest(argv);

        return reactiveClient.executeRequest(request);
    }

    protected ReactiveRequest buildRequest(Object[] argv){

        Map<String, ?> substitutionsMap = methodMetadata.indexToName().entrySet().stream()
                .flatMap(e->e.getValue().stream()
                        .map(v->new AbstractMap.SimpleImmutableEntry<>(e.getKey(), v)))
                .collect(Collectors.toMap(Map.Entry::getValue, entry -> argv[entry.getKey()]));

        HttpMethod method = HttpMethod.resolve(methodMetadata.template().method());
        URI uri = defaultUriBuilderFactory
                .uriString(methodMetadata.template().url())
                .queryParams(parameters(argv))
                .build(substitutionsMap);

        return new ReactiveRequest(method, uri, headers(argv, substitutionsMap), body(argv));
    }

    protected MultiValueMap<String, String> parameters(Object[] argv){
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        methodMetadata.template().queries()
                .forEach((key, value) -> parameters.addAll(key, (List)value));

        if(methodMetadata.formParams() != null){
            methodMetadata.formParams().forEach(param -> parameters.add(param, "{"+param+"}"));
        }

        if(methodMetadata.queryMapIndex() != null){
            ((Map<String, String>)argv[methodMetadata.queryMapIndex()])
                    .forEach((key, value) -> parameters.add(key, value));
        }
        return parameters;
    }

    protected MultiValueMap<String, String> headers(Object[] argv, Map<String, ?> substitutionsMap){

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

        methodMetadata.template().headers().keySet().forEach(headerName ->
            headers.addAll(headerName, headerExpanders.get(headerName).stream()
                    .map(expander -> expander.apply(substitutionsMap))
                    .collect(toList()))
        );

        if(methodMetadata.headerMapIndex() != null){
            ((Map<String, String>)argv[methodMetadata.headerMapIndex()]).forEach(headers::add);
        }

        return headers;
    }

    protected Publisher<Object> body(Object[] argv){
        if(methodMetadata.bodyIndex() != null){
            Object body = argv[methodMetadata.bodyIndex()];
            if(body instanceof Publisher){
                return (Publisher<Object>) body;
            } else {
                return Mono.just(body);
            }
        } else {
            return Mono.empty();
        }
    }

    //TODO refactor to chunks instead of regexp for better performance
    private Function<Map<String, ?>, String> buildExpandHeaderFunction(final String headerPattern){
        return substitutionsMap -> {
            String headerExpanded = headerPattern;
            for(Map.Entry<String, ?> entry : substitutionsMap.entrySet()){
                Pattern substitutionPattern = Pattern.compile("{"+entry.getKey()+"}", Pattern.LITERAL);
                headerExpanded = substitutionPattern.matcher(headerExpanded).replaceAll(entry.getValue().toString());
            }
            return headerExpanded;
        };
    }

    public static class Factory implements ReactiveMethodHandlerFactory {
        private final ReactiveClientFactory reactiveClientFactory;

        public Factory(final ReactiveClientFactory reactiveClientFactory) {
            this.reactiveClientFactory = checkNotNull(reactiveClientFactory, "client must not be null");
        }

        @Override
        public ReactiveClientMethodHandler create(Target target, final MethodMetadata metadata) {

            return new ReactiveClientMethodHandler(
                    target,
                    metadata,
                    reactiveClientFactory.apply(metadata));
        }
    }
}
