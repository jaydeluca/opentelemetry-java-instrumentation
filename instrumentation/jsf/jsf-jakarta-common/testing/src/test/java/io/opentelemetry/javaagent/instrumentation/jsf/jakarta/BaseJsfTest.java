package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import java.util.stream.Stream;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

abstract class BaseJsfTest extends AbstractHttpServerUsingTest<Server> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();


  @Override
  protected Server setupServer() throws Exception {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"));

    Resource extraResource = Resource.newSystemResource("test-app-extra");
    if (extraResource != null) {
      webAppContext.getMetaData().addWebInfResource(extraResource);
    }

    Server jettyServer = new Server(port);
    // connector config to localhost??
    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  @ParameterizedTest
  @ArgumentsSource(SimplifyArgs.class)
  void testPath(String path, String route) {
    AggregatedHttpResponse response = client.get(address.resolve(path).toString()).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("Hello");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName(getContextPath() + "/hello.xhtml")
                .hasKind(SpanKind.SERVER)
                .hasAttributesSatisfyingExactly(
                    equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                    equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                    equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                    satisfies(
                        NetworkAttributes.NETWORK_PEER_PORT,
                        val -> val.isInstanceOf(Long.class)),
                    equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                    equalTo(UrlAttributes.URL_SCHEME, "http"),
                    equalTo(UrlAttributes.URL_PATH, getContextPath() + "/" + path),
                    equalTo(UserAgentAttributes.USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                    equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                    equalTo(HttpAttributes.HTTP_ROUTE, getContextPath() + "/" + route),
                    satisfies(
                        ClientAttributes.CLIENT_ADDRESS,
                        val ->
                            val.satisfiesAnyOf(
                                v -> assertThat(v).isEqualTo(TEST_CLIENT_IP), v -> assertThat(v).isNull()))
                )
        )
    );
  }

  static class SimplifyArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("hello.xhtml", "*.xhtml"),
          Arguments.of("faces/hello.xhtml", "faces/*.xhtml")
      );
    }
  }

  @Test
  void testGreeting() {
    AggregatedHttpResponse response = client.get(address.resolve("greeting.xhtml").toString()).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("title").text()).isEqualTo("Hello, World!");

      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(
              span -> span.hasName(getContextPath() + "/greeting.xhtml")
                  .hasKind(SpanKind.SERVER)
                  .hasNoParent()
                  .hasAttributes(Attributes.empty())));

      testing.clearData();

  }
}
