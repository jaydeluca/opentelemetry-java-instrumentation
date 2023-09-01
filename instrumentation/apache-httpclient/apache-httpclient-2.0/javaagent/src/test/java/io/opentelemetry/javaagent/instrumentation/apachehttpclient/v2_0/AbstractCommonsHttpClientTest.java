/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpRequest;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.TraceMethod;
import java.util.Map;

abstract class AbstractCommonsHttpClientTest< extends HttpRequest> extends AbstractHttpClientTest<T> {

  private static final HttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();

  private final HttpClient client = buildClient(false);
  private final HttpClient clientWithReadTimeout = buildClient(true);

  HttpClient buildClient(boolean readTimeout) {
    HttpClient client = new HttpClient(connectionManager);
    client.setConnectionTimeout(CONNECTION_TIMEOUT.toMillisPart());
    if (readTimeout) {
      client.setTimeout(READ_TIMEOUT.toMillisPart());
    }
    return client;
  }

  HttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  public T buildRequest(String method, java.net.URI uri, Map<String, String> headers) {
    HttpMethod request;

    switch (method) {
      case "GET":
        request = new GetMethod(uri.toString());
      case "PUT":
        request = new PutMethod(uri.toString());
        break;
      case "POST":
        request = new PostMethod(uri.toString());
        break;
      case "HEAD":
        request = new HeadMethod(uri.toString());
        break;
      case "DELETE":
        request = new DeleteMethod(uri.toString());
        break;
      case "OPTIONS":
        request = new OptionsMethod(uri.toString());
        break;
      case "TRACE":
        request = new TraceMethod(uri.toString());
        break;
      default:
        throw new IllegalStateException("Unsupported method: " + method);
      }

      headers.forEach(request::setRequestHeader);
      return (T) request;
  }

}
