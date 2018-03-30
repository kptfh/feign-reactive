# feign-reactive

[![Build Status](https://travis-ci.org/OpenFeign/feign-vertx.svg?branch=master)](https://travis-ci.org/OpenFeign/feign-vertx)
[ ![Download](https://api.bintray.com/packages/hosuaby/OpenFeign/feign-vertx/images/download.svg) ](https://bintray.com/hosuaby/OpenFeign/feign-vertx/_latestVersion)

Use Feign with Spring WebFlux

## Overview

Implementation of Feign on Spring WebClient. Brings you the best of two worlds together : 
concise syntax of Feign to write client side API on fast, asynchronous and
non-blocking HTTP client of Spring WebClient.

## Usage

Write Feign API as usual, but every method of interface
 - may accept `org.reactivestreams.Publisher` as body
 - must return `reactor.core.publisher.Mono` or `reactor.core.publisher.Flux`.

```java
@Headers({ "Accept: application/json" })
public interface IcecreamServiceApi {

  @RequestLine("GET /icecream/flavors")
  Flux<Flavor> getAvailableFlavors();

  @RequestLine("GET /icecream/mixins")
  Flux<Mixin> getAvailableMixins();

  @RequestLine("POST /icecream/orders")
  @Headers("Content-Type: application/json")
  Mono<Bill> makeOrder(IceCreamOrder order);

  @RequestLine("GET /icecream/orders/{orderId}")
  Mono<IceCreamOrder> findOrder(@Param("orderId") int orderId);

  @RequestLine("POST /icecream/bills/pay")
  @Headers("Content-Type: application/json")
  Mono<Void> payBill(Publisher<Bill> bill);
}
```
Build the client :

```java

/* Create instance of your API */
IcecreamServiceApi client = ReactiveFeign
    .builder()
    .webClient(WebClient.create())
    .encoder(new JacksonEncoder(TestUtils.MAPPER))
    .logger(new Slf4jLogger())
    .logLevel(Logger.Level.FULL)
    .target(IcecreamServiceApi.class, "http://www.icecreame.com")

/* Execute nonblocking requests */
Flux<Flavor> flavors = icecreamApi.getAvailableFlavors();
Flux<Mixin> mixins = icecreamApi.getAvailableMixins();
```

or cloud aware client :

```java
 TestInterface client = CloudReactiveFeign.<TestInterface>builder()
    .webClient(WebClient.create())
    .encoder(new JacksonEncoder(new ObjectMapper()))
    .setHystrixCommandSetterFactory(new DefaultSetterFactory())
    .setFallback(new TestInterface() {
        @Override
        public Mono<String> get() {
            return Mono.just("fallback");
        }
    })
    .setLoadBalancerCommand(
         LoadBalancerCommand.builder()
                 .withLoadBalancer(AbstractLoadBalancer.class.cast(getNamedLoadBalancer(serviceName)))
                 .withRetryHandler(new DefaultLoadBalancerRetryHandler(1, 1, true))
                 .build()
    )
    .target(TestInterface.class, "http://" + serviceName);

/* Execute nonblocking requests */
Flux<Flavor> flavors = icecreamApi.getAvailableFlavors();
Flux<Mixin> mixins = icecreamApi.getAvailableMixins();
```

## Maven

```xml
<repositories>
    <repository>
        <id>bintray-kptfh-feign-reactive</id>
        <name>bintray</name>
        <url>https://dl.bintray.com/kptfh/feign-reactive</url>
    </repository>
</repositories>
...
<dependencies>
    ...
    <dependency>
        <groupId>io.github.reactivefeign</groupId>
        <artifactId>feign-reactive-core</artifactId>
        <version>0.5.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.reactivefeign</groupId>
        <artifactId>feign-reactive-cloud</artifactId>
        <version>0.5.0</version>
    </dependency>
    ...
</dependencies>
```

## License

Library distributed under Apache License Version 2.0.