package reactivefeign;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.Consumer;

public class ReactiveUtils {

    public static  <T> Subscriber<T> onNext(Consumer<T> consumer){
        return new Subscriber<T>() {
            @Override
            public void onNext(T t) {
                consumer.accept(t);
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        };
    }
}
