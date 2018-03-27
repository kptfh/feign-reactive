package reactivefeign;

import feign.Request;

/**
 * @author Sergii Karpenko
 */
public class ReactiveOptions extends Request.Options {

    private boolean tryUseCompression;

    public ReactiveOptions(int connectTimeoutMillis, int readTimeoutMillis) {
        this(connectTimeoutMillis, readTimeoutMillis, false);
    }

    public ReactiveOptions(int connectTimeoutMillis, int readTimeoutMillis, boolean tryUseCompression) {
        super(connectTimeoutMillis, readTimeoutMillis);
        this.tryUseCompression = tryUseCompression;
    }

    public boolean isTryUseCompression() {
        return tryUseCompression;
    }
}
