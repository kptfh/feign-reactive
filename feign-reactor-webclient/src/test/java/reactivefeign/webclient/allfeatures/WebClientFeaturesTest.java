package reactivefeign.webclient.allfeatures;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.test.context.junit4.SpringRunner;
import reactivefeign.allfeatures.AllFeaturesController;
import reactivefeign.webclient.WebReactiveFeign;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.nio.ByteBuffer.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.empty;
import static reactor.core.publisher.Mono.fromFuture;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {"spring.main.web-application-type=reactive"},
        classes = {WebClientFeaturesController.class },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
public class WebClientFeaturesTest {

    private WebClientFeaturesApi client;

    @LocalServerPort
    private int port;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        client = WebReactiveFeign.<WebClientFeaturesApi>builder()
                .decode404()
                .target(WebClientFeaturesApi.class, "http://localhost:" + port);
    }

    @Test
    public void shouldMirrorStreamingBinaryBodyReactive() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(2);

        AtomicInteger sentCount = new AtomicInteger();
        ConcurrentLinkedQueue<DataBuffer> receivedAll = new ConcurrentLinkedQueue<>();

        CompletableFuture<DataBuffer> firstReceived = new CompletableFuture<>();

        Flux<DataBuffer> returned = client
                .mirrorStreamingBinaryBodyReactive(Flux.just(
                        fromByteArray(new byte[]{1,2,3}),
                        fromByteArray(new byte[]{4,5,6})))
                .delayUntil(testObject -> sentCount.get() == 1 ? fromFuture(firstReceived)
                        : empty())
                .doOnNext(sent -> sentCount.incrementAndGet());

        returned.doOnNext(received -> {
            receivedAll.add(received);
            assertThat(receivedAll.size()).isEqualTo(sentCount.get());
            firstReceived.complete(received);
            countDownLatch.countDown();
        }).subscribe();

        countDownLatch.await();

        assertThat(receivedAll.stream().map(DataBuffer::asByteBuffer).collect(Collectors.toList()))
                .containsExactly(wrap(new byte[]{1,2,3}), wrap(new byte[]{4,5,6}));
    }

    private static DataBuffer fromByteArray(byte[] data){
        return new DefaultDataBufferFactory().wrap(data);
    }

    @Test
    public void shouldMirrorResourceReactiveWithZeroCopying(){
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ByteArrayResource resource = new ByteArrayResource(data);
        Flux<DataBuffer> returned = client.mirrorResourceReactiveWithZeroCopying(resource);
        assertThat(DataBufferUtils.join(returned).block().asByteBuffer()).isEqualTo(wrap(data));
    }


}
