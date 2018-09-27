package reactivefeign.rx2.client.statushandler;

import reactivefeign.client.ReactiveHttpResponse;
import reactivefeign.client.statushandler.ReactiveStatusHandler;
import reactor.core.publisher.Mono;

import static reactor.adapter.rxjava.RxJava2Adapter.singleToMono;

public class Rx2ReactiveStatusHandler implements ReactiveStatusHandler {

	private final Rx2StatusHandler statusHandler;

	public Rx2ReactiveStatusHandler(Rx2StatusHandler statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean shouldHandle(int status) {
		return statusHandler.shouldHandle(status);
	}

	@Override
	public Mono<? extends Throwable> decode(String methodKey, ReactiveHttpResponse response) {
		return singleToMono(statusHandler.decode(methodKey, response));
	}
}
