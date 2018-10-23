/**
 * Copyright 2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package reactivefeign.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.ReactiveFeign;
import reactivefeign.ReactiveOptions;

import java.util.concurrent.TimeUnit;

import static reactivefeign.webclient.client.WebReactiveHttpClient.webClient;

/**
 * {@link WebClient} based implementation of reactive Feign
 *
 * @author Sergii Karpenko
 */
public class WebReactiveFeign {

    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;

    public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public static <T> Builder<T> builder(WebClient webClient) {
        return new Builder<>(webClient);
    }

  public static class Builder<T> extends ReactiveFeign.Builder<T> {

      protected WebClient webClient;

      protected Builder() {
          this(WebClient.create());
      }

      protected Builder(WebClient webClient) {
          setWebClient(webClient);
          options(new ReactiveOptions.Builder()
                  .setConnectTimeoutMillis(DEFAULT_CONNECT_TIMEOUT_MILLIS)
                  .setReadTimeoutMillis(DEFAULT_READ_TIMEOUT_MILLIS)
                  .build());
      }

      @Override
      public Builder<T> options(ReactiveOptions options) {
          if (!options.isEmpty()) {
              ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
                      opts -> {
                          if (options.getConnectTimeoutMillis() != null) {
                              opts.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                      options.getConnectTimeoutMillis().intValue());
                          }
                          if (options.getReadTimeoutMillis() != null) {
                              opts.afterNettyContextInit(ctx -> {
                                  ctx.addHandlerLast(new ReadTimeoutHandler(
                                          options.getReadTimeoutMillis(),
                                          TimeUnit.MILLISECONDS));

                              });
                          }
                          if (options.isTryUseCompression() != null) {
                              opts.compression(options.isTryUseCompression());
                          }
                      });

              setWebClient(webClient.mutate().clientConnector(connector).build());
          }
          return this;
      }

      protected void setWebClient(WebClient webClient){
          this.webClient = webClient;
          clientFactory(methodMetadata -> webClient(methodMetadata, webClient));
      }
  }
}


