/*
 * Copyright (C) 2013 Square, Inc.
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

import io.honeyqa.client.network.okhttp.internal.http.HQ_OkHeaders;
import java.util.Collections;
import java.util.List;

import static io.honeyqa.client.network.okhttp.internal.http.HQ_StatusLine.HTTP_PERM_REDIRECT;
import static io.honeyqa.client.network.okhttp.internal.http.HQ_StatusLine.HTTP_TEMP_REDIRECT;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * An HTTP response. Instances of this class are not immutable: the response
 * body is a one-shot value that may be consumed only once. All other properties
 * are immutable.
 */
public final class HQ_Response {
  private final HQ_Request request;
  private final HQ_Protocol protocol;
  private final int code;
  private final String message;
  private final HQ_Handshake handshake;
  private final HQ_Headers headers;
  private final HQ_ResponseBody body;
  private HQ_Response networkResponse;
  private HQ_Response cacheResponse;
  private final HQ_Response priorResponse;

  private volatile HQ_CacheControl cacheControl; // Lazily initialized.

  private HQ_Response(Builder builder) {
    this.request = builder.request;
    this.protocol = builder.protocol;
    this.code = builder.code;
    this.message = builder.message;
    this.handshake = builder.handshake;
    this.headers = builder.headers.build();
    this.body = builder.body;
    this.networkResponse = builder.networkResponse;
    this.cacheResponse = builder.cacheResponse;
    this.priorResponse = builder.priorResponse;
  }

  /**
   * The wire-level request that initiated this HTTP response. This is not
   * necessarily the same request issued by the application:
   * <ul>
   *     <li>It may be transformed by the HTTP client. For example, the client
   *         may copy headers like {@code Content-Length} from the request body.
   *     <li>It may be the request generated in response to an HTTP redirect or
   *         authentication challenge. In this case the request URL may be
   *         different than the initial request URL.
   * </ul>
   */
  public HQ_Request request() {
    return request;
  }

  /**
   * Returns the HTTP protocol, such as {@link HQ_Protocol#HTTP_1_1} or {@link
   * HQ_Protocol#HTTP_1_0}.
   */
  public HQ_Protocol protocol() {
    return protocol;
  }

  /** Returns the HTTP status code. */
  public int code() {
    return code;
  }

  /**
   * Returns true if the code is in [200..300), which means the request was
   * successfully received, understood, and accepted.
   */
  public boolean isSuccessful() {
    return code >= 200 && code < 300;
  }

  /** Returns the HTTP status message or null if it is unknown. */
  public String message() {
    return message;
  }

  /**
   * Returns the TLS handshake of the connection that carried this response, or
   * null if the response was received without TLS.
   */
  public HQ_Handshake handshake() {
    return handshake;
  }

  public List<String> headers(String name) {
    return headers.values(name);
  }

  public String header(String name) {
    return header(name, null);
  }

  public String header(String name, String defaultValue) {
    String result = headers.get(name);
    return result != null ? result : defaultValue;
  }

  public HQ_Headers headers() {
    return headers;
  }

  public HQ_ResponseBody body() {
    return body;
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  /** Returns true if this response redirects to another resource. */
  public boolean isRedirect() {
    switch (code) {
      case HTTP_PERM_REDIRECT:
      case HTTP_TEMP_REDIRECT:
      case HTTP_MULT_CHOICE:
      case HTTP_MOVED_PERM:
      case HTTP_MOVED_TEMP:
      case HTTP_SEE_OTHER:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns the raw response received from the network. Will be null if this
   * response didn't use the network, such as when the response is fully cached.
   * The body of the returned response should not be read.
   */
  public HQ_Response networkResponse() {
    return networkResponse;
  }

  /**
   * Returns the raw response received from the cache. Will be null if this
   * response didn't use the cache. For conditional get requests the cache
   * response and network response may both be non-null. The body of the
   * returned response should not be read.
   */
  public HQ_Response cacheResponse() {
    return cacheResponse;
  }

  /**
   * Returns the response for the HTTP redirect or authorization challenge that
   * triggered this response, or null if this response wasn't triggered by an
   * automatic retry. The body of the returned response should not be read
   * because it has already been consumed by the redirecting client.
   */
  public HQ_Response priorResponse() {
    return priorResponse;
  }

  /**
   * Returns the authorization challenges appropriate for this response's code.
   * If the response code is 401 unauthorized, this returns the
   * "WWW-Authenticate" challenges. If the response code is 407 proxy
   * unauthorized, this returns the "Proxy-Authenticate" challenges. Otherwise
   * this returns an empty list of challenges.
   */
  public List<HQ_Challenge> challenges() {
    String responseField;
    if (code == HTTP_UNAUTHORIZED) {
      responseField = "WWW-Authenticate";
    } else if (code == HTTP_PROXY_AUTH) {
      responseField = "Proxy-Authenticate";
    } else {
      return Collections.emptyList();
    }
    return HQ_OkHeaders.parseChallenges(headers(), responseField);
  }

  /**
   * Returns the cache control directives for this response. This is never null,
   * even if this response contains no {@code HQ_Cache-Control} header.
   */
  public HQ_CacheControl cacheControl() {
    HQ_CacheControl result = cacheControl;
    return result != null ? result : (cacheControl = HQ_CacheControl.parse(headers));
  }

  @Override public String toString() {
    return "HQ_Response{protocol="
        + protocol
        + ", code="
        + code
        + ", message="
        + message
        + ", url="
        + request.urlString()
        + '}';
  }

  public static class Builder {
    private HQ_Request request;
    private HQ_Protocol protocol;
    private int code = -1;
    private String message;
    private HQ_Handshake handshake;
    private HQ_Headers.Builder headers;
    private HQ_ResponseBody body;
    private HQ_Response networkResponse;
    private HQ_Response cacheResponse;
    private HQ_Response priorResponse;

    public Builder() {
      headers = new HQ_Headers.Builder();
    }

    private Builder(HQ_Response response) {
      this.request = response.request;
      this.protocol = response.protocol;
      this.code = response.code;
      this.message = response.message;
      this.handshake = response.handshake;
      this.headers = response.headers.newBuilder();
      this.body = response.body;
      this.networkResponse = response.networkResponse;
      this.cacheResponse = response.cacheResponse;
      this.priorResponse = response.priorResponse;
    }

    public Builder request(HQ_Request request) {
      this.request = request;
      return this;
    }

    public Builder protocol(HQ_Protocol protocol) {
      this.protocol = protocol;
      return this;
    }

    public Builder code(int code) {
      this.code = code;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder handshake(HQ_Handshake handshake) {
      this.handshake = handshake;
      return this;
    }

    /**
     * Sets the header named {@code name} to {@code value}. If this request
     * already has any headers with that name, they are all replaced.
     */
    public Builder header(String name, String value) {
      headers.set(name, value);
      return this;
    }

    /**
     * Adds a header with {@code name} and {@code value}. Prefer this method for
     * multiply-valued headers like "Set-Cookie".
     */
    public Builder addHeader(String name, String value) {
      headers.add(name, value);
      return this;
    }

    public Builder removeHeader(String name) {
      headers.removeAll(name);
      return this;
    }

    /** Removes all headers on this builder and adds {@code headers}. */
    public Builder headers(HQ_Headers headers) {
      this.headers = headers.newBuilder();
      return this;
    }

    public Builder body(HQ_ResponseBody body) {
      this.body = body;
      return this;
    }

    public Builder networkResponse(HQ_Response networkResponse) {
      if (networkResponse != null) checkSupportResponse("networkResponse", networkResponse);
      this.networkResponse = networkResponse;
      return this;
    }

    public Builder cacheResponse(HQ_Response cacheResponse) {
      if (cacheResponse != null) checkSupportResponse("cacheResponse", cacheResponse);
      this.cacheResponse = cacheResponse;
      return this;
    }

    private void checkSupportResponse(String name, HQ_Response response) {
      if (response.body != null) {
        throw new IllegalArgumentException(name + ".body != null");
      } else if (response.networkResponse != null) {
        throw new IllegalArgumentException(name + ".networkResponse != null");
      } else if (response.cacheResponse != null) {
        throw new IllegalArgumentException(name + ".cacheResponse != null");
      } else if (response.priorResponse != null) {
        throw new IllegalArgumentException(name + ".priorResponse != null");
      }
    }

    public Builder priorResponse(HQ_Response priorResponse) {
      if (priorResponse != null) checkPriorResponse(priorResponse);
      this.priorResponse = priorResponse;
      return this;
    }

    private void checkPriorResponse(HQ_Response response) {
      if (response.body != null) {
        throw new IllegalArgumentException("priorResponse.body != null");
      }
    }

    public HQ_Response build() {
      if (request == null) throw new IllegalStateException("request == null");
      if (protocol == null) throw new IllegalStateException("protocol == null");
      if (code < 0) throw new IllegalStateException("code < 0: " + code);
      return new HQ_Response(this);
    }
  }
}
