package reactivefeign.client;

import feign.MethodMetadata;

import java.util.function.Function;

/**
 * @author Sergii Karpenko
 */

public interface ReactiveClientFactory extends Function<MethodMetadata, ReactiveClient> {
}
