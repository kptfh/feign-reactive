package reactivefeign.client.statushandler;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Sergii Karpenko
 */
public class DefaultFeignErrorDecoder implements ReactiveStatusHandler{

    private final ErrorDecoder errorDecoder;

    public DefaultFeignErrorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
    }

    @Override
    public boolean shouldHandle(HttpStatus status) {
        return status.isError();
    }

    @Override
    public Mono<? extends Throwable> decode(String methodKey, ClientResponse response) {
        return response
                .bodyToMono(ByteArrayResource.class)
                .map(ByteArrayResource::getByteArray).defaultIfEmpty(new byte[0])
                .map(bodyData -> errorDecoder.decode(methodKey,
                        Response.builder()
                                .status(response.statusCode().value())
                                .reason(response.statusCode()
                                        .getReasonPhrase())
                                .headers(response.headers().asHttpHeaders()
                                        .entrySet().stream()
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue)))
                                .body(bodyData).build()));
    }

}
