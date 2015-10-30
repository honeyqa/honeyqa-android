package io.honeyqa.client.network.okhttp.internal.http;

import io.honeyqa.client.network.okhttp.HQ_HttpUrl;
import io.honeyqa.client.network.okhttp.HQ_Protocol;
import io.honeyqa.client.network.okhttp.HQ_Request;

import java.net.HttpURLConnection;
import java.net.Proxy;

public final class HQ_RequestLine {
  private HQ_RequestLine() {
  }

  /**
   * Returns the request status line, like "GET / HTTP/1.1". This is exposed
   * to the application by {@link HttpURLConnection#getHeaderFields}, so it
   * needs to be set even if the transport is SPDY.
   */
  static String get(HQ_Request request, Proxy.Type proxyType, HQ_Protocol protocol) {
    StringBuilder result = new StringBuilder();
    result.append(request.method());
    result.append(' ');

    if (includeAuthorityInRequestLine(request, proxyType)) {
      result.append(request.httpUrl());
    } else {
      result.append(requestPath(request.httpUrl()));
    }

    result.append(' ');
    result.append(version(protocol));
    return result.toString();
  }

  /**
   * Returns true if the request line should contain the full URL with host
   * and port (like "GET http://android.com/foo HTTP/1.1") or only the path
   * (like "GET /foo HTTP/1.1").
   */
  private static boolean includeAuthorityInRequestLine(HQ_Request request, Proxy.Type proxyType) {
    return !request.isHttps() && proxyType == Proxy.Type.HTTP;
  }

  /**
   * Returns the path to request, like the '/' in 'GET / HTTP/1.1'. Never empty,
   * even if the request URL is. Includes the query component if it exists.
   */
  public static String requestPath(HQ_HttpUrl url) {
    String path = url.encodedPath();
    String query = url.encodedQuery();
    return query != null ? (path + '?' + query) : path;
  }

  public static String version(HQ_Protocol protocol) {
    return protocol == HQ_Protocol.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1";
  }
}
