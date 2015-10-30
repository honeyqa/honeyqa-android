/*
 * Copyright (C) 2012 Square, Inc.
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

import io.honeyqa.client.network.okhttp.internal.HQ_Internal;
import io.honeyqa.client.network.okhttp.internal.HQ_InternalCache;
import io.honeyqa.client.network.okhttp.internal.HQ_Network;
import io.honeyqa.client.network.okhttp.internal.HQ_RouteDatabase;
import io.honeyqa.client.network.okhttp.internal.HQ_Util;
import io.honeyqa.client.network.okhttp.internal.http.HQ_AuthenticatorAdapter;
import io.honeyqa.client.network.okhttp.internal.http.HQ_HttpEngine;
import io.honeyqa.client.network.okhttp.internal.http.HQ_RouteException;
import io.honeyqa.client.network.okhttp.internal.http.HQ_Transport;
import io.honeyqa.client.network.okhttp.internal.tls.HQ_OkHostnameVerifier;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import io.honeyqa.client.network.okio.BufferedSink;
import io.honeyqa.client.network.okio.BufferedSource;

/**
 * Configures and creates HTTP connections. Most applications can use a single
 * HQ_OkHttpClient for all of their HTTP requests - benefiting from a shared
 * response cache, thread pool, connection re-use, etc.
 *
 * <p>Instances of HQ_OkHttpClient are intended to be fully configured before they're
 * shared - once shared they should be treated as immutable and can safely be used
 * to concurrently open new connections. If required, threads can call
 * {@link #clone()} to make a shallow copy of the HQ_OkHttpClient that can be
 * safely modified with further configuration changes.
 */
public class HQ_OkHttpClient implements Cloneable {
  private static final List<HQ_Protocol> DEFAULT_PROTOCOLS = HQ_Util.immutableList(
          HQ_Protocol.HTTP_2, HQ_Protocol.SPDY_3, HQ_Protocol.HTTP_1_1);

  private static final List<HQ_ConnectionSpec> DEFAULT_CONNECTION_SPECS = HQ_Util.immutableList(
          HQ_ConnectionSpec.MODERN_TLS, HQ_ConnectionSpec.COMPATIBLE_TLS, HQ_ConnectionSpec.CLEARTEXT);

  static {
    HQ_Internal.instance = new HQ_Internal() {
      @Override public HQ_Transport newTransport(
          HQ_Connection connection, HQ_HttpEngine httpEngine) throws IOException {
        return connection.newTransport(httpEngine);
      }

      @Override public boolean clearOwner(HQ_Connection connection) {
        return connection.clearOwner();
      }

      @Override public void closeIfOwnedBy(HQ_Connection connection, Object owner) throws IOException {
        connection.closeIfOwnedBy(owner);
      }

      @Override public int recycleCount(HQ_Connection connection) {
        return connection.recycleCount();
      }

      @Override public void setProtocol(HQ_Connection connection, HQ_Protocol protocol) {
        connection.setProtocol(protocol);
      }

      @Override public void setOwner(HQ_Connection connection, HQ_HttpEngine httpEngine) {
        connection.setOwner(httpEngine);
      }

      @Override public boolean isReadable(HQ_Connection pooled) {
        return pooled.isReadable();
      }

      @Override public void addLenient(HQ_Headers.Builder builder, String line) {
        builder.addLenient(line);
      }

      @Override public void addLenient(HQ_Headers.Builder builder, String name, String value) {
        builder.addLenient(name, value);
      }

      @Override public void setCache(HQ_OkHttpClient client, HQ_InternalCache internalCache) {
        client.setInternalCache(internalCache);
      }

      @Override public HQ_InternalCache internalCache(HQ_OkHttpClient client) {
        return client.internalCache();
      }

      @Override public void recycle(HQ_ConnectionPool pool, HQ_Connection connection) {
        pool.recycle(connection);
      }

      @Override public HQ_RouteDatabase routeDatabase(HQ_OkHttpClient client) {
        return client.routeDatabase();
      }

      @Override public HQ_Network network(HQ_OkHttpClient client) {
        return client.network;
      }

      @Override public void setNetwork(HQ_OkHttpClient client, HQ_Network network) {
        client.network = network;
      }

      @Override public void connectAndSetOwner(HQ_OkHttpClient client, HQ_Connection connection,
          HQ_HttpEngine owner, HQ_Request request) throws HQ_RouteException {
        connection.connectAndSetOwner(client, owner, request);
      }

      @Override
      public void callEnqueue(HQ_Call call, HQ_Callback responseCallback, boolean forWebSocket) {
        call.enqueue(responseCallback, forWebSocket);
      }

      @Override public void callEngineReleaseConnection(HQ_Call call) throws IOException {
        call.engine.releaseConnection();
      }

      @Override public HQ_Connection callEngineGetConnection(HQ_Call call) {
        return call.engine.getConnection();
      }

      @Override public BufferedSource connectionRawSource(HQ_Connection connection) {
        return connection.rawSource();
      }

      @Override public BufferedSink connectionRawSink(HQ_Connection connection) {
        return connection.rawSink();
      }

      @Override public void connectionSetOwner(HQ_Connection connection, Object owner) {
        connection.setOwner(owner);
      }

      @Override
      public void apply(HQ_ConnectionSpec tlsConfiguration, SSLSocket sslSocket, boolean isFallback) {
        tlsConfiguration.apply(sslSocket, isFallback);
      }

      @Override public HQ_HttpUrl getHttpUrlChecked(String url)
          throws MalformedURLException, UnknownHostException {
        return HQ_HttpUrl.getChecked(url);
      }
    };
  }

  /** Lazily-initialized. */
  private static SSLSocketFactory defaultSslSocketFactory;

  private final HQ_RouteDatabase routeDatabase;
  private HQ_Dispatcher dispatcher;
  private Proxy proxy;
  private List<HQ_Protocol> protocols;
  private List<HQ_ConnectionSpec> connectionSpecs;
  private final List<HQ_Interceptor> interceptors = new ArrayList<>();
  private final List<HQ_Interceptor> networkInterceptors = new ArrayList<>();
  private ProxySelector proxySelector;
  private CookieHandler cookieHandler;

  /** Non-null if this client is caching; possibly by {@code cache}. */
  private HQ_InternalCache internalCache;
  private HQ_Cache cache;

  private SocketFactory socketFactory;
  private SSLSocketFactory sslSocketFactory;
  private HostnameVerifier hostnameVerifier;
  private HQ_CertificatePinner certificatePinner;
  private HQ_Authenticator authenticator;
  private HQ_ConnectionPool connectionPool;
  private HQ_Network network;
  private boolean followSslRedirects = true;
  private boolean followRedirects = true;
  private boolean retryOnConnectionFailure = true;
  private int connectTimeout = 10_000;
  private int readTimeout = 10_000;
  private int writeTimeout = 10_000;

  public HQ_OkHttpClient() {
    routeDatabase = new HQ_RouteDatabase();
    dispatcher = new HQ_Dispatcher();
  }

  private HQ_OkHttpClient(HQ_OkHttpClient okHttpClient) {
    this.routeDatabase = okHttpClient.routeDatabase;
    this.dispatcher = okHttpClient.dispatcher;
    this.proxy = okHttpClient.proxy;
    this.protocols = okHttpClient.protocols;
    this.connectionSpecs = okHttpClient.connectionSpecs;
    this.interceptors.addAll(okHttpClient.interceptors);
    this.networkInterceptors.addAll(okHttpClient.networkInterceptors);
    this.proxySelector = okHttpClient.proxySelector;
    this.cookieHandler = okHttpClient.cookieHandler;
    this.cache = okHttpClient.cache;
    this.internalCache = cache != null ? cache.internalCache : okHttpClient.internalCache;
    this.socketFactory = okHttpClient.socketFactory;
    this.sslSocketFactory = okHttpClient.sslSocketFactory;
    this.hostnameVerifier = okHttpClient.hostnameVerifier;
    this.certificatePinner = okHttpClient.certificatePinner;
    this.authenticator = okHttpClient.authenticator;
    this.connectionPool = okHttpClient.connectionPool;
    this.network = okHttpClient.network;
    this.followSslRedirects = okHttpClient.followSslRedirects;
    this.followRedirects = okHttpClient.followRedirects;
    this.retryOnConnectionFailure = okHttpClient.retryOnConnectionFailure;
    this.connectTimeout = okHttpClient.connectTimeout;
    this.readTimeout = okHttpClient.readTimeout;
    this.writeTimeout = okHttpClient.writeTimeout;
  }

  /**
   * Sets the default connect timeout for new connections. A value of 0 means no timeout, otherwise
   * values must be between 1 and {@link Integer#MAX_VALUE} when converted to milliseconds.
   *
   * @see URLConnection#setConnectTimeout(int)
   */
  public void setConnectTimeout(long timeout, TimeUnit unit) {
    if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
    if (unit == null) throw new IllegalArgumentException("unit == null");
    long millis = unit.toMillis(timeout);
    if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
    if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
    connectTimeout = (int) millis;
  }

  /** Default connect timeout (in milliseconds). */
  public int getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Sets the default read timeout for new connections. A value of 0 means no timeout, otherwise
   * values must be between 1 and {@link Integer#MAX_VALUE} when converted to milliseconds.
   *
   * @see URLConnection#setReadTimeout(int)
   */
  public void setReadTimeout(long timeout, TimeUnit unit) {
    if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
    if (unit == null) throw new IllegalArgumentException("unit == null");
    long millis = unit.toMillis(timeout);
    if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
    if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
    readTimeout = (int) millis;
  }

  /** Default read timeout (in milliseconds). */
  public int getReadTimeout() {
    return readTimeout;
  }

  /**
   * Sets the default write timeout for new connections. A value of 0 means no timeout, otherwise
   * values must be between 1 and {@link Integer#MAX_VALUE} when converted to milliseconds.
   */
  public void setWriteTimeout(long timeout, TimeUnit unit) {
    if (timeout < 0) throw new IllegalArgumentException("timeout < 0");
    if (unit == null) throw new IllegalArgumentException("unit == null");
    long millis = unit.toMillis(timeout);
    if (millis > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeout too large.");
    if (millis == 0 && timeout > 0) throw new IllegalArgumentException("Timeout too small.");
    writeTimeout = (int) millis;
  }

  /** Default write timeout (in milliseconds). */
  public int getWriteTimeout() {
    return writeTimeout;
  }

  /**
   * Sets the HTTP proxy that will be used by connections created by this
   * client. This takes precedence over {@link #setProxySelector}, which is
   * only honored when this proxy is null (which it is by default). To disable
   * proxy use completely, call {@code setProxy(Proxy.NO_PROXY)}.
   */
  public HQ_OkHttpClient setProxy(Proxy proxy) {
    this.proxy = proxy;
    return this;
  }

  public Proxy getProxy() {
    return proxy;
  }

  /**
   * Sets the proxy selection policy to be used if no {@link #setProxy proxy}
   * is specified explicitly. The proxy selector may return multiple proxies;
   * in that case they will be tried in sequence until a successful connection
   * is established.
   *
   * <p>If unset, the {@link ProxySelector#getDefault() system-wide default}
   * proxy selector will be used.
   */
  public HQ_OkHttpClient setProxySelector(ProxySelector proxySelector) {
    this.proxySelector = proxySelector;
    return this;
  }

  public ProxySelector getProxySelector() {
    return proxySelector;
  }

  /**
   * Sets the cookie handler to be used to read outgoing cookies and write
   * incoming cookies.
   *
   * <p>If unset, the {@link CookieHandler#getDefault() system-wide default}
   * cookie handler will be used.
   */
  public HQ_OkHttpClient setCookieHandler(CookieHandler cookieHandler) {
    this.cookieHandler = cookieHandler;
    return this;
  }

  public CookieHandler getCookieHandler() {
    return cookieHandler;
  }

  /** Sets the response cache to be used to read and write cached responses. */
  void setInternalCache(HQ_InternalCache internalCache) {
    this.internalCache = internalCache;
    this.cache = null;
  }

  HQ_InternalCache internalCache() {
    return internalCache;
  }

  public HQ_OkHttpClient setCache(HQ_Cache cache) {
    this.cache = cache;
    this.internalCache = null;
    return this;
  }

  public HQ_Cache getCache() {
    return cache;
  }

  /**
   * Sets the socket factory used to create connections. OkHttp only uses
   * the parameterless {@link SocketFactory#createSocket() createSocket()}
   * method to create unconnected sockets. Overriding this method,
   * e. g., allows the socket to be bound to a specific local address.
   *
   * <p>If unset, the {@link SocketFactory#getDefault() system-wide default}
   * socket factory will be used.
   */
  public HQ_OkHttpClient setSocketFactory(SocketFactory socketFactory) {
    this.socketFactory = socketFactory;
    return this;
  }

  public SocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * Sets the socket factory used to secure HTTPS connections.
   *
   * <p>If unset, a lazily created SSL socket factory will be used.
   */
  public HQ_OkHttpClient setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
    this.sslSocketFactory = sslSocketFactory;
    return this;
  }

  public SSLSocketFactory getSslSocketFactory() {
    return sslSocketFactory;
  }

  /**
   * Sets the verifier used to confirm that response certificates apply to
   * requested hostnames for HTTPS connections.
   *
   * <p>If unset, a default hostname verifier will be used.
   */
  public HQ_OkHttpClient setHostnameVerifier(HostnameVerifier hostnameVerifier) {
    this.hostnameVerifier = hostnameVerifier;
    return this;
  }

  public HostnameVerifier getHostnameVerifier() {
    return hostnameVerifier;
  }

  /**
   * Sets the certificate pinner that constrains which certificates are trusted.
   * By default HTTPS connections rely on only the {@link #setSslSocketFactory
   * SSL socket factory} to establish trust. Pinning certificates avoids the
   * need to trust certificate authorities.
   */
  public HQ_OkHttpClient setCertificatePinner(HQ_CertificatePinner certificatePinner) {
    this.certificatePinner = certificatePinner;
    return this;
  }

  public HQ_CertificatePinner getCertificatePinner() {
    return certificatePinner;
  }

  /**
   * Sets the authenticator used to respond to challenges from the remote web
   * server or proxy server.
   *
   * <p>If unset, the {@link java.net.Authenticator#setDefault system-wide default}
   * authenticator will be used.
   */
  public HQ_OkHttpClient setAuthenticator(HQ_Authenticator authenticator) {
    this.authenticator = authenticator;
    return this;
  }

  public HQ_Authenticator getAuthenticator() {
    return authenticator;
  }

  /**
   * Sets the connection pool used to recycle HTTP and HTTPS connections.
   *
   * <p>If unset, the {@link HQ_ConnectionPool#getDefault() system-wide
   * default} connection pool will be used.
   */
  public HQ_OkHttpClient setConnectionPool(HQ_ConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
    return this;
  }

  public HQ_ConnectionPool getConnectionPool() {
    return connectionPool;
  }

  /**
   * Configure this client to follow redirects from HTTPS to HTTP and from HTTP
   * to HTTPS.
   *
   * <p>If unset, protocol redirects will be followed. This is different than
   * the built-in {@code HttpURLConnection}'s default.
   */
  public HQ_OkHttpClient setFollowSslRedirects(boolean followProtocolRedirects) {
    this.followSslRedirects = followProtocolRedirects;
    return this;
  }

  public boolean getFollowSslRedirects() {
    return followSslRedirects;
  }

  /** Configure this client to follow redirects. If unset, redirects be followed. */
  public void setFollowRedirects(boolean followRedirects) {
    this.followRedirects = followRedirects;
  }

  public boolean getFollowRedirects() {
    return followRedirects;
  }

  /**
   * Configure this client to retry or not when a connectivity problem is encountered. By default,
   * this client silently recovers from the following problems:
   *
   * <ul>
   *   <li><strong>Unreachable IP addresses.</strong> If the URL's host has multiple IP addresses,
   *       failure to reach any individual IP address doesn't fail the overall request. This can
   *       increase availability of multi-homed services.
   *   <li><strong>Stale pooled connections.</strong> The {@link HQ_ConnectionPool} reuses sockets
   *       to decrease request latency, but these connections will occasionally time out.
   *   <li><strong>Unreachable proxy servers.</strong> A {@link ProxySelector} can be used to
   *       attempt multiple proxy servers in sequence, eventually falling back to a direct
   *       connection.
   * </ul>
   *
   * Set this to false to avoid retrying requests when doing so is destructive. In this case the
   * calling application should do its own recovery of connectivity failures.
   */
  public void setRetryOnConnectionFailure(boolean retryOnConnectionFailure) {
    this.retryOnConnectionFailure = retryOnConnectionFailure;
  }

  public boolean getRetryOnConnectionFailure() {
    return retryOnConnectionFailure;
  }

  HQ_RouteDatabase routeDatabase() {
    return routeDatabase;
  }

  /**
   * Sets the dispatcher used to set policy and execute asynchronous requests.
   * Must not be null.
   */
  public HQ_OkHttpClient setDispatcher(HQ_Dispatcher dispatcher) {
    if (dispatcher == null) throw new IllegalArgumentException("dispatcher == null");
    this.dispatcher = dispatcher;
    return this;
  }

  public HQ_Dispatcher getDispatcher() {
    return dispatcher;
  }

  /**
   * Configure the protocols used by this client to communicate with remote
   * servers. By default this client will prefer the most efficient transport
   * available, falling back to more ubiquitous protocols. Applications should
   * only call this method to avoid specific compatibility problems, such as web
   * servers that behave incorrectly when SPDY is enabled.
   *
   * <p>The following protocols are currently supported:
   * <ul>
   *   <li><a href="http://www.w3.org/Protocols/rfc2616/rfc2616.html">http/1.1</a>
   *   <li><a href="http://www.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1">spdy/3.1</a>
   *   <li><a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-17">h2</a>
   * </ul>
   *
   * <p><strong>This is an evolving set.</strong> Future releases include
   * support for transitional protocols. The http/1.1 transport will never be
   * dropped.
   *
   * <p>If multiple protocols are specified, <a
   * href="http://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a>
   * will be used to negotiate a transport.
   *
   * <p>{@link HQ_Protocol#HTTP_1_0} is not supported in this set. Requests are
   * initiated with {@code HTTP/1.1} only. If the server responds with {@code
   * HTTP/1.0}, that will be exposed by {@link HQ_Response#protocol()}.
   *
   * @param protocols the protocols to use, in order of preference. The list
   *     must contain {@link HQ_Protocol#HTTP_1_1}. It must not contain null or
   *     {@link HQ_Protocol#HTTP_1_0}.
   */
  public HQ_OkHttpClient setProtocols(List<HQ_Protocol> protocols) {
    protocols = HQ_Util.immutableList(protocols);
    if (!protocols.contains(HQ_Protocol.HTTP_1_1)) {
      throw new IllegalArgumentException("protocols doesn't contain http/1.1: " + protocols);
    }
    if (protocols.contains(HQ_Protocol.HTTP_1_0)) {
      throw new IllegalArgumentException("protocols must not contain http/1.0: " + protocols);
    }
    if (protocols.contains(null)) {
      throw new IllegalArgumentException("protocols must not contain null");
    }
    this.protocols = HQ_Util.immutableList(protocols);
    return this;
  }

  public List<HQ_Protocol> getProtocols() {
    return protocols;
  }

  public HQ_OkHttpClient setConnectionSpecs(List<HQ_ConnectionSpec> connectionSpecs) {
    this.connectionSpecs = HQ_Util.immutableList(connectionSpecs);
    return this;
  }

  public List<HQ_ConnectionSpec> getConnectionSpecs() {
    return connectionSpecs;
  }

  /**
   * Returns a modifiable list of interceptors that observe the full span of each call: from before
   * the connection is established (if any) until after the response source is selected (either the
   * origin server, cache, or both).
   */
  public List<HQ_Interceptor> interceptors() {
    return interceptors;
  }

  /**
   * Returns a modifiable list of interceptors that observe a single network request and response.
   * These interceptors must call {@link HQ_Interceptor.Chain#proceed} exactly once: it is an error for
   * a network interceptor to short-circuit or repeat a network request.
   */
  public List<HQ_Interceptor> networkInterceptors() {
    return networkInterceptors;
  }

  /**
   * Prepares the {@code request} to be executed at some point in the future.
   */
  public HQ_Call newCall(HQ_Request request) {
    return new HQ_Call(this, request);
  }

  /**
   * Cancels all scheduled or in-flight calls tagged with {@code tag}. Requests
   * that are already complete cannot be canceled.
   */
  public HQ_OkHttpClient cancel(Object tag) {
    getDispatcher().cancel(tag);
    return this;
  }

  /**
   * Returns a shallow copy of this HQ_OkHttpClient that uses the system-wide
   * default for each field that hasn't been explicitly configured.
   */
  HQ_OkHttpClient copyWithDefaults() {
    HQ_OkHttpClient result = new HQ_OkHttpClient(this);
    if (result.proxySelector == null) {
      result.proxySelector = ProxySelector.getDefault();
    }
    if (result.cookieHandler == null) {
      result.cookieHandler = CookieHandler.getDefault();
    }
    if (result.socketFactory == null) {
      result.socketFactory = SocketFactory.getDefault();
    }
    if (result.sslSocketFactory == null) {
      result.sslSocketFactory = getDefaultSSLSocketFactory();
    }
    if (result.hostnameVerifier == null) {
      result.hostnameVerifier = HQ_OkHostnameVerifier.INSTANCE;
    }
    if (result.certificatePinner == null) {
      result.certificatePinner = HQ_CertificatePinner.DEFAULT;
    }
    if (result.authenticator == null) {
      result.authenticator = HQ_AuthenticatorAdapter.INSTANCE;
    }
    if (result.connectionPool == null) {
      result.connectionPool = HQ_ConnectionPool.getDefault();
    }
    if (result.protocols == null) {
      result.protocols = DEFAULT_PROTOCOLS;
    }
    if (result.connectionSpecs == null) {
      result.connectionSpecs = DEFAULT_CONNECTION_SPECS;
    }
    if (result.network == null) {
      result.network = HQ_Network.DEFAULT;
    }
    return result;
  }

  /**
   * Java and Android programs default to using a single global SSL context,
   * accessible to HTTP clients as {@link SSLSocketFactory#getDefault()}. If we
   * used the shared SSL context, when OkHttp enables ALPN for its SPDY-related
   * stuff, it would also enable ALPN for other usages, which might crash them
   * because ALPN is enabled when it isn't expected to be.
   *
   * <p>This code avoids that by defaulting to an OkHttp-created SSL context.
   * The drawback of this approach is that apps that customize the global SSL
   * context will lose these customizations.
   */
  private synchronized SSLSocketFactory getDefaultSSLSocketFactory() {
    if (defaultSslSocketFactory == null) {
      try {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        defaultSslSocketFactory = sslContext.getSocketFactory();
      } catch (GeneralSecurityException e) {
        throw new AssertionError(); // The system has no TLS. Just give up.
      }
    }
    return defaultSslSocketFactory;
  }

  /** Returns a shallow copy of this HQ_OkHttpClient. */
  @Override public HQ_OkHttpClient clone() {
    return new HQ_OkHttpClient(this);
  }
}
