# feign-reactive

[![Build Status](https://travis-ci.org/OpenFeign/feign-vertx.svg?branch=master)](https://travis-ci.org/OpenFeign/feign-vertx)
[ ![Download](https://api.bintray.com/packages/hosuaby/OpenFeign/feign-vertx/images/download.svg) ](https://bintray.com/hosuaby/OpenFeign/feign-vertx/_latestVersion)

Use Feign with Spring WebFlux

## Overview

Implementation of Feign on Spring WebClient. Brings you the best of two worlds together : 
concise syntax of Feign to write client side API on fast, asynchronous and
non-blocking HTTP client of Spring WebClient.

## Usage

Write Feign API as usual, but every method of interface must return
`reactor.core.publisher.Mono or reactor.core.publisher.Flux`.

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
  Mono<Void> payBill(Bill bill);
}
```
Build the client :

```java
WebClient webClient = WebClient.create()

/* Create instance of your API */
client = ReactiveFeign
    .builder()
    .webClient(webClient)
    //encodes body and parameters
    .encoder(new JacksonEncoder(TestUtils.MAPPER))
    .logger(new Slf4jLogger())
    .logLevel(Logger.Level.FULL)
    .target(IcecreamServiceApi.class, "http://www.icecreame.com")

/* Execute requests asynchronously */
Flux<Flavor> flavorsFuture = icecreamApi.getAvailableFlavors();
Flux<Mixin> mixinsFuture = icecreamApi.getAvailableMixins();
```

## Maven

```xml
<repositories>
    <repository>
        <id>jcenter</id>
        <url>https://jcenter.bintray.com/</url>
    </repository>
</repositories>
...
<dependencies>
    ...
    <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-reactive</artifactId>
        <version>0.1.0</version>
    </dependency>
    ...
</dependencies>
```

## License

Library distributed under Apache License Version 2.0.