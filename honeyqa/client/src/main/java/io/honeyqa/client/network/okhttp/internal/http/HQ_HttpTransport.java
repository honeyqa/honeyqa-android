/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.honeyqa.client.network.okhttp.internal.http;

import io.honeyqa.client.network.okhttp.HQ_Request;
import io.honeyqa.client.network.okhttp.HQ_Response;
import io.honeyqa.client.network.okhttp.HQ_ResponseBody;
import java.io.IOException;
import io.honeyqa.client.network.okio.Okio;
import io.honeyqa.client.network.okio.Sink;
import io.honeyqa.client.network.okio.Source;

public final class HQ_HttpTransport implements HQ_Transport {
  private final HQ_HttpEngine httpEngine;
  private final HQ_HttpConnection httpConnection;

  public HQ_HttpTransport(HQ_HttpEngine httpEngine, HQ_HttpConnection httpConnection) {
    this.httpEngine = httpEngine;
    this.httpConnection = httpConnection;
  }

  @Override public Sink createRequestBody(HQ_Request request, long contentLength) throws IOException {
    if ("chunked".equalsIgnoreCase(request.header("Transfer-Encoding"))) {
      // Stream a request body of unknown length.
      return httpConnection.newChunkedSink();
    }

    if (contentLength != -1) {
      // Stream a request body of a known length.
      return httpConnection.newFixedLengthSink(contentLength);
    }

    throw new IllegalStateException(
        "Cannot stream a request body without chunked encoding or a known content length!");
  }

  @Override public void finishRequest() throws IOException {
    httpConnection.flush();
  }

  @Override public void writeRequestBody(HQ_RetryableSink requestBody) throws IOException {
    httpConnection.writeRequestBody(requestBody);
  }

  /**
   * Prepares the HTTP headers and sends them to the server.
   *
   * <p>For streaming requests with a body, headers must be prepared
   * <strong>before</strong> the output stream has been written to. Otherwise
   * the body would need to be buffered!
   *
   * <p>For non-streaming requests with a body, headers must be prepared
   * <strong>after</strong> the output stream has been written to and closed.
   * This ensures that the {@code Content-Length} header field receives the
   * proper value.
   */
  public void writeRequestHeaders(HQ_Request request) throws IOException {
    httpEngine.writingRequestHeaders();
    String requestLine = HQ_RequestLine.get(request,
            httpEngine.getConnection().getRoute().getProxy().type(),
            httpEngine.getConnection().getProtocol());
    httpConnection.writeRequest(request.headers(), requestLine);
  }

  @Override public HQ_Response.Builder readResponseHeaders() throws IOException {
    return httpConnection.readResponse();
  }

  @Override public void releaseConnectionOnIdle() throws IOException {
    if (canReuseConnection()) {
      httpConnection.poolOnIdle();
    } else {
      httpConnection.closeOnIdle();
    }
  }

  @Override public boolean canReuseConnection() {
    // If the request specified that the connection shouldn't be reused, don't reuse it.
    if ("close".equalsIgnoreCase(httpEngine.getRequest().header("HQ_Connection"))) {
      return false;
    }

    // If the response specified that the connection shouldn't be reused, don't reuse it.
    if ("close".equalsIgnoreCase(httpEngine.getResponse().header("HQ_Connection"))) {
      return false;
    }

    if (httpConnection.isClosed()) {
      return false;
    }

    return true;
  }

  @Override public HQ_ResponseBody openResponseBody(HQ_Response response) throws IOException {
    Source source = getTransferStream(response);
    return new HQ_RealResponseBody(response.headers(), Okio.buffer(source));
  }

  private Source getTransferStream(HQ_Response response) throws IOException {
    if (!HQ_HttpEngine.hasBody(response)) {
      return httpConnection.newFixedLengthSource(0);
    }

    if ("chunked".equalsIgnoreCase(response.header("Transfer-Encoding"))) {
      return httpConnection.newChunkedSource(httpEngine);
    }

    long contentLength = HQ_OkHeaders.contentLength(response);
    if (contentLength != -1) {
      return httpConnection.newFixedLengthSource(contentLength);
    }

    // Wrap the input stream from the connection (rather than just returning
    // "socketIn" directly here), so that we can control its use after the
    // reference escapes.
    return httpConnection.newUnknownLengthSource();
  }

  @Override public void disconnect(HQ_HttpEngine engine) throws IOException {
    httpConnection.closeIfOwnedBy(engine);
  }
}
