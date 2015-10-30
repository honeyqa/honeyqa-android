/*
 * Copyright (C) 2014 Square, Inc.
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
package io.honeyqa.client.network.okhttp;

import io.honeyqa.client.network.okhttp.internal.HQ_NamedRunnable;
import io.honeyqa.client.network.okhttp.internal.http.HQ_HttpEngine;
import io.honeyqa.client.network.okhttp.internal.http.HQ_RequestException;
import io.honeyqa.client.network.okhttp.internal.http.HQ_RouteException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.logging.Level;

import static io.honeyqa.client.network.okhttp.internal.HQ_Internal.logger;
import static io.honeyqa.client.network.okhttp.internal.http.HQ_HttpEngine.MAX_FOLLOW_UPS;

/**
 * A call is a request that has been prepared for execution. A call can be
 * canceled. As this object represents a single request/response pair (stream),
 * it cannot be executed twice.
 */
public class HQ_Call {
  private final HQ_OkHttpClient client;

  // Guarded by this.
  private boolean executed;
  volatile boolean canceled;

  /** The application's original request unadulterated by redirects or auth headers. */
  HQ_Request originalRequest;
  HQ_HttpEngine engine;

  protected HQ_Call(HQ_OkHttpClient client, HQ_Request originalRequest) {
    // Copy the client. Otherwise changes (socket factory, redirect policy,
    // etc.) may incorrectly be reflected in the request when it is executed.
    this.client = client.copyWithDefaults();
    this.originalRequest = originalRequest;
  }

  /**
   * Invokes the request immediately, and blocks until the response can be
   * processed or is in error.
   *
   * <p>The caller may read the response body with the response's
   * {@link HQ_Response#body} method.  To facilitate connection recycling, callers
   * should always {@link HQ_ResponseBody#close() close the response body}.
   *
   * <p>Note that transport-layer success (receiving a HTTP response code,
   * headers and body) does not necessarily indicate application-layer success:
   * {@code response} may still indicate an unhappy HTTP response code like 404
   * or 500.
   *
   * @throws IOException if the request could not be executed due to
   *     cancellation, a connectivity problem or timeout. Because networks can
   *     fail during an exchange, it is possible that the remote server
   *     accepted the request before the failure.
   *
   * @throws IllegalStateException when the call has already been executed.
   */
  public HQ_Response execute() throws IOException {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    try {
      client.getDispatcher().executed(this);
      HQ_Response result = getResponseWithInterceptorChain(false);
      if (result == null) throw new IOException("Canceled");
      return result;
    } finally {
      client.getDispatcher().finished(this);
    }
  }

  Object tag() {
    return originalRequest.tag();
  }

  /**
   * Schedules the request to be executed at some point in the future.
   *
   * <p>The {@link HQ_OkHttpClient#getDispatcher dispatcher} defines when the
   * request will run: usually immediately unless there are several other
   * requests currently being executed.
   *
   * <p>This client will later call back {@code responseCallback} with either
   * an HTTP response or a failure exception. If you {@link #cancel} a request
   * before it completes the callback will not be invoked.
   *
   * @throws IllegalStateException when the call has already been executed.
   */
  public void enqueue(HQ_Callback responseCallback) {
    enqueue(responseCallback, false);
  }

  void enqueue(HQ_Callback responseCallback, boolean forWebSocket) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    client.getDispatcher().enqueue(new AsyncCall(responseCallback, forWebSocket));
  }

  /**
   * Cancels the request, if possible. Requests that are already complete
   * cannot be canceled.
   */
  public void cancel() {
    canceled = true;
    if (engine != null) engine.disconnect();
  }

  public boolean isCanceled() {
    return canceled;
  }

  final class AsyncCall extends HQ_NamedRunnable {
    private final HQ_Callback responseCallback;
    private final boolean forWebSocket;

    private AsyncCall(HQ_Callback responseCallback, boolean forWebSocket) {
      super("OkHttp %s", originalRequest.urlString());
      this.responseCallback = responseCallback;
      this.forWebSocket = forWebSocket;
    }

    String host() {
      return originalRequest.httpUrl().host();
    }

    HQ_Request request() {
      return originalRequest;
    }

    Object tag() {
      return originalRequest.tag();
    }

    void cancel() {
      HQ_Call.this.cancel();
    }

    HQ_Call get() {
      return HQ_Call.this;
    }

    @Override protected void execute() {
      boolean signalledCallback = false;
      try {
        HQ_Response response = getResponseWithInterceptorChain(forWebSocket);
        if (canceled) {
          signalledCallback = true;
          responseCallback.onFailure(originalRequest, new IOException("Canceled"));
        } else {
          signalledCallback = true;
          responseCallback.onResponse(response);
        }
      } catch (IOException e) {
        if (signalledCallback) {
          // Do not signal the callback twice!
          logger.log(Level.INFO, "HQ_Callback failure for " + toLoggableString(), e);
        } else {
          responseCallback.onFailure(engine.getRequest(), e);
        }
      } finally {
        client.getDispatcher().finished(this);
      }
    }
  }

  /**
   * Returns a string that describes this call. Doesn't include a full URL as that might contain
   * sensitive information.
   */
  private String toLoggableString() {
    String string = canceled ? "canceled call" : "call";
    HQ_HttpUrl redactedUrl = originalRequest.httpUrl().resolve("/...");
    return string + " to " + redactedUrl;
  }

  private HQ_Response getResponseWithInterceptorChain(boolean forWebSocket) throws IOException {
    HQ_Interceptor.Chain chain = new ApplicationInterceptorChain(0, originalRequest, forWebSocket);
    return chain.proceed(originalRequest);
  }

  class ApplicationInterceptorChain implements HQ_Interceptor.Chain {
    private final int index;
    private final HQ_Request request;
    private final boolean forWebSocket;

    ApplicationInterceptorChain(int index, HQ_Request request, boolean forWebSocket) {
      this.index = index;
      this.request = request;
      this.forWebSocket = forWebSocket;
    }

    @Override public HQ_Connection connection() {
      return null;
    }

    @Override public HQ_Request request() {
      return request;
    }

    @Override public HQ_Response proceed(HQ_Request request) throws IOException {
      if (index < client.interceptors().size()) {
        // There's another interceptor in the chain. HQ_Call that.
        HQ_Interceptor.Chain chain = new ApplicationInterceptorChain(index + 1, request, forWebSocket);
        return client.interceptors().get(index).intercept(chain);
      } else {
        // No more interceptors. Do HTTP.
        return getResponse(request, forWebSocket);
      }
    }
  }

  /**
   * Performs the request and returns the response. May return null if this
   * call was canceled.
   */
  HQ_Response getResponse(HQ_Request request, boolean forWebSocket) throws IOException {
    // Copy body metadata to the appropriate request headers.
    HQ_RequestBody body = request.body();
    if (body != null) {
      HQ_Request.Builder requestBuilder = request.newBuilder();

      HQ_MediaType contentType = body.contentType();
      if (contentType != null) {
        requestBuilder.header("Content-Type", contentType.toString());
      }

      long contentLength = body.contentLength();
      if (contentLength != -1) {
        requestBuilder.header("Content-Length", Long.toString(contentLength));
        requestBuilder.removeHeader("Transfer-Encoding");
      } else {
        requestBuilder.header("Transfer-Encoding", "chunked");
        requestBuilder.removeHeader("Content-Length");
      }

      request = requestBuilder.build();
    }

    // Create the initial HTTP engine. Retries and redirects need new engine for each attempt.
    engine = new HQ_HttpEngine(client, request, false, false, forWebSocket, null, null, null, null);

    int followUpCount = 0;
    while (true) {
      if (canceled) {
        engine.releaseConnection();
        throw new IOException("Canceled");
      }

      try {
        engine.sendRequest();
        engine.readResponse();
      } catch (HQ_RequestException e) {
        // The attempt to interpret the request failed. Give up.
        throw e.getCause();
      } catch (HQ_RouteException e) {
        // The attempt to connect via a route failed. The request will not have been sent.
        HQ_HttpEngine retryEngine = engine.recover(e);
        if (retryEngine != null) {
          engine = retryEngine;
          continue;
        }
        // Give up; recovery is not possible.
        throw e.getLastConnectException();
      } catch (IOException e) {
        // An attempt to communicate with a server failed. The request may have been sent.
        HQ_HttpEngine retryEngine = engine.recover(e, null);
        if (retryEngine != null) {
          engine = retryEngine;
          continue;
        }

        // Give up; recovery is not possible.
        throw e;
      }

      HQ_Response response = engine.getResponse();
      HQ_Request followUp = engine.followUpRequest();

      if (followUp == null) {
        if (!forWebSocket) {
          engine.releaseConnection();
        }
        return response;
      }

      if (++followUpCount > MAX_FOLLOW_UPS) {
        throw new ProtocolException("Too many follow-up requests: " + followUpCount);
      }

      if (!engine.sameConnection(followUp.httpUrl())) {
        engine.releaseConnection();
      }

      HQ_Connection connection = engine.close();
      request = followUp;
      engine = new HQ_HttpEngine(client, request, false, false, forWebSocket, connection, null, null,
          response);
    }
  }
}
