package feign.reactive;

import feign.ReactiveFeign;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Should check that client is not blocking
 *
 * @author Sergii Karpenko
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = {TestController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
public class ReactiveFeignLoadTest {

    @LocalServerPort
    private int port;


    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String targetUrl;

    private TestInterface client;

    @Before
    public void setUp() {
        targetUrl = "http://localhost:" + port;
        client = ReactiveFeign.<TestInterface>builder()
                .webClient(WebClient.create())
                .target(TestInterface.class, targetUrl);
    }

    @Test
    public void shouldNotBlock() throws IOException, InterruptedException {
        performLoadTest(client);
    }

    private void performLoadTest(TestInterface client) throws InterruptedException {

        long testStart = System.currentTimeMillis();

        int callsNo = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(callsNo);

        for(int i = 0; i < callsNo; i++){
            client.get(System.currentTimeMillis()).subscribe(
                    start -> {
//                        System.out.println("********************"+(System.currentTimeMillis() - start)
//                                +":"+Thread.activeCount()+":"+Thread.currentThread().getName());
                        countDownLatch.countDown();
                    }
            );
//            LockSupport.parkNanos(1);
        }

        long timeToPost = System.currentTimeMillis() - testStart;

        countDownLatch.await();

        System.out.println("Time to post:"+timeToPost);
        System.out.println("Time to run:"+(System.currentTimeMillis() - testStart));

//        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
//        for (ThreadInfo ti : threadMxBean.dumpAllThreads(true, true)) {
//            System.out.print(ti.toString());
//        }

    }

//    @Configuration
//    public static class Config {
//        @Primary
//        @Bean
//        public ReactiveWebServerFactory reactiveWebServerFactory (){
//            return new NettyReactiveWebServerFactory();
//        }
//    }
}
