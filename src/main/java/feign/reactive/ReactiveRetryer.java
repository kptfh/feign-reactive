package feign.reactive;

import feign.Client;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;

import java.util.Date;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Sergii Karpenko
 *
 * Feign {@link feign.Retryer} can't be used due to it's blocking nature.
 *
 * Cloned for each invocation to {@link Client#execute(Request, feign.Request.Options)}.
 * Implementations may keep state to determine if retry operations should continue or not.
 *
 */
public interface ReactiveRetryer {

    /**
     * @return period in milliseconds when perform retry in.
     * -1 if no need to perform retry
     */
    long doRetryIn(Throwable e);

    ReactiveRetryer clone();

    public static class Default implements ReactiveRetryer {

        private final int maxAttempts;
        private final long period;
        private final long maxPeriod;
        int attempt;

        public Default() {
            this(100, SECONDS.toMillis(1), 5);
        }

        public Default(long period, long maxPeriod, int maxAttempts) {
            this.period = period;
            this.maxPeriod = maxPeriod;
            this.maxAttempts = maxAttempts;
            this.attempt = 1;
        }

        // visible for testing;
        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        public long doRetryIn(Throwable e) {
            if (attempt++ >= maxAttempts) {
                return -1;
            }

            long interval;
            Date retryAfter;
            if (e instanceof RetryableException && (retryAfter = ((RetryableException)e).retryAfter()) != null) {
                interval = retryAfter.getTime() - currentTimeMillis();
                interval = Math.min(interval, maxPeriod);
                interval = Math.max(interval, 0);
            } else {
                interval = nextMaxInterval();
            }

            return interval;
        }

        /**
         * Calculates the time interval to a retry attempt. <br> The interval increases exponentially
         * with each attempt, at a rate of nextInterval *= 1.5 (where 1.5 is the backoff factor), to the
         * maximum interval.
         *
         * @return time in nanoseconds from now until the next attempt.
         */
        long nextMaxInterval() {
            long interval = (long) (period * Math.pow(1.5, attempt - 1));
            return interval > maxPeriod ? maxPeriod : interval;
        }

        @Override
        public ReactiveRetryer clone() {
            return new Default(period, maxPeriod, maxAttempts);
        }
    }

    /**
     * Implementation that never retries request. It propagates the RetryableException.
     */
    Retryer NEVER_RETRY = new Retryer() {

        @Override
        public void continueOrPropagate(RetryableException e) {
            throw e;
        }

        @Override
        public Retryer clone() {
            return this;
        }
    };
}
