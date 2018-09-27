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

package reactivefeign.rx2;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.reactivex.Single;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactivefeign.ReactiveFeign;
import reactivefeign.client.LoggerReactiveHttpClient;
import reactivefeign.rx2.testcase.IcecreamServiceApi;
import reactivefeign.rx2.testcase.domain.Bill;
import reactivefeign.rx2.testcase.domain.IceCreamOrder;
import reactivefeign.rx2.testcase.domain.OrderGenerator;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Sergii Karpenko
 */
public class LoggerTest {

  public static final String LOGGER_NAME = LoggerReactiveHttpClient.class.getName();
  @ClassRule
  public static WireMockClassRule wireMockRule = new WireMockClassRule(
      wireMockConfig()
          .asynchronousResponseEnabled(true)
          .dynamicPort());

  protected ReactiveFeign.Builder<IcecreamServiceApi> builder(){
    return Rx2ReactiveFeign.builder();
  }

  protected Appender appender;

  @Test
  public void shouldLog() throws Exception {

    setLogLevel(Level.TRACE);

    IceCreamOrder order = new OrderGenerator().generate(20);
    Bill billExpected = Bill.makeBill(order);

    wireMockRule.stubFor(post(urlEqualTo("/icecream/orders"))
        .withRequestBody(equalTo(TestUtils.MAPPER.writeValueAsString(order)))
        .willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(TestUtils.MAPPER.writeValueAsString(billExpected))));

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class,
            "http://localhost:" + wireMockRule.port());

    Single<Bill> billMono = client.makeOrder(order);

    // no logs before subscription
    ArgumentCaptor<LogEvent> argumentCaptor = ArgumentCaptor.forClass(LogEvent.class);
    Mockito.verify(appender, never()).append(argumentCaptor.capture());

    billMono.blockingGet();

    Mockito.verify(appender, times(7)).append(argumentCaptor.capture());

    List<LogEvent> logEvents = argumentCaptor.getAllValues();
    assertLogEvent(logEvents, 0, Level.DEBUG,
        "[IcecreamServiceApi#makeOrder]--->POST http://localhost");
    assertLogEvent(logEvents, 1, Level.TRACE,
        "[IcecreamServiceApi#makeOrder] REQUEST HEADERS\n" +
            "Accept:[application/json]");
    assertLogEvent(logEvents, 2, Level.TRACE,
        "[IcecreamServiceApi#makeOrder] REQUEST BODY\n" +
            "IceCreamOrder{ id=20, balls=");
    assertLogEvent(logEvents, 3, Level.TRACE,
        "[IcecreamServiceApi#makeOrder] RESPONSE HEADERS\n" +
            "Content-Type:application/json");
    assertLogEvent(logEvents, 4, Level.DEBUG,
        "[IcecreamServiceApi#makeOrder]<--- headers takes");
    assertLogEvent(logEvents, 5, Level.TRACE,
        "[IcecreamServiceApi#makeOrder] RESPONSE BODY\n" +
            "reactivefeign.rx2.testcase.domain.Bill");
    assertLogEvent(logEvents, 6, Level.DEBUG,
        "[IcecreamServiceApi#makeOrder]<--- body takes");
  }

  private void assertLogEvent(List<LogEvent> events, int index, Level level, String message) {
    assertThat(events).element(index)
        .hasFieldOrPropertyWithValue("level", level)
        .extracting("message")
        .extractingResultOf("getFormattedMessage")
        .have(new Condition<>(o -> ((String) o).contains(message), "check message"));
  }

  @Before
  public void before() {
    appender = Mockito.mock(Appender.class);
    when(appender.getName()).thenReturn("TestAppender");
    when(appender.isStarted()).thenReturn(true);
    getLoggerConfig().addAppender(appender, Level.ALL, null);
  }

  private static void setLogLevel(Level logLevel) {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    Configuration configuration = loggerContext.getConfiguration();
    configuration.getLoggerConfig(LOGGER_NAME).setLevel(logLevel);
    loggerContext.updateLoggers();
  }

  private static LoggerConfig getLoggerConfig() {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    Configuration configuration = loggerContext.getConfiguration();
    configuration.addLogger(LOGGER_NAME, new LoggerConfig());
    return configuration.getLoggerConfig(LOGGER_NAME);
  }
}
