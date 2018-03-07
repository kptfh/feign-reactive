package feign.reactive;

import feign.Contract;
import feign.MethodMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static feign.Util.checkNotNull;

/**
 * Contract allowing only {@link Mono} and {@link Flux} return type.
 *
 * @author Sergii Karpenko
 */
public class ReactiveDelegatingContract implements Contract {

    private final Contract delegate;

    public ReactiveDelegatingContract(final Contract delegate) {
        this.delegate = checkNotNull(delegate, "delegate must not be null");
    }

    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(
            final Class<?> targetType) {
        final List<MethodMetadata> metadatas =
                this.delegate.parseAndValidatateMetadata(targetType);

        for (final MethodMetadata metadata : metadatas) {
            final Type type = metadata.returnType();

            if (!(type instanceof ParameterizedType)
                    || !((ParameterizedType) type).getRawType().equals(Mono.class)
                    && !((ParameterizedType) type).getRawType().equals(Flux.class)) {
                throw new IllegalArgumentException(String.format(
                        "Method %s of contract %s doesn't returns reactor.core.publisher.Mono or reactor.core.publisher.Flux",
                        metadata.configKey(), targetType.getSimpleName()));
            }
        }

        return metadatas;
    }
}
