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
package io.honeyqa.client.network.okhttp.internal;

import io.honeyqa.client.network.okhttp.HQ_Call;
import io.honeyqa.client.network.okhttp.HQ_Callback;
import io.honeyqa.client.network.okhttp.HQ_Connection;
import io.honeyqa.client.network.okhttp.HQ_ConnectionPool;
import io.honeyqa.client.network.okhttp.HQ_ConnectionSpec;
import io.honeyqa.client.network.okhttp.HQ_Headers;
import io.honeyqa.client.network.okhttp.HQ_HttpUrl;
import io.honeyqa.client.network.okhttp.HQ_OkHttpClient;
import io.honeyqa.client.network.okhttp.HQ_Protocol;
import io.honeyqa.client.network.okhttp.HQ_Request;
import io.honeyqa.client.network.okhttp.internal.http.HQ_HttpEngine;
import io.honeyqa.client.network.okhttp.internal.http.HQ_RouteException;
import io.honeyqa.client.network.okhttp.internal.http.HQ_Transport;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;
import io.honeyqa.client.network.okio.BufferedSink;
import io.honeyqa.client.network.okio.BufferedSource;

/**
 * Escalate internal APIs in {@code io.honeyqa.client.network.okhttp} so they can be used
 * from OkHttp's implementation packages. The only implementation of this
 * interface is in {@link HQ_OkHttpClient}.
 */
public abstract class HQ_Internal {
  public static final Logger logger = Logger.getLogger(HQ_OkHttpClient.class.getName());

  public static void initializeInstanceForTests() {
    // Needed in tests to ensure that the instance is actually pointing to something.
    new HQ_OkHttpClient();
  }

  public static HQ_Internal instance;

  public abstract HQ_Transport newTransport(HQ_Connection connection, HQ_HttpEngine httpEngine)
      throws IOException;

  public abstract boolean clearOwner(HQ_Connection connection);

  public abstract void closeIfOwnedBy(HQ_Connection connection, Object owner) throws IOException;

  public abstract int recycleCount(HQ_Connection connection);

  public abstract void setProtocol(HQ_Connection connection, HQ_Protocol protocol);

  public abstract void setOwner(HQ_Connection connection, HQ_HttpEngine httpEngine);

  public abstract boolean isReadable(HQ_Connection pooled);

  public abstract void addLenient(HQ_Headers.Builder builder, String line);

  public abstract void addLenient(HQ_Headers.Builder builder, String name, String value);

  public abstract void setCache(HQ_OkHttpClient client, HQ_InternalCache internalCache);

  public abstract HQ_InternalCache internalCache(HQ_OkHttpClient client);

  public abstract void recycle(HQ_ConnectionPool pool, HQ_Connection connection);

  public abstract HQ_RouteDatabase routeDatabase(HQ_OkHttpClient client);

  public abstract HQ_Network network(HQ_OkHttpClient client);

  public abstract void setNetwork(HQ_OkHttpClient client, HQ_Network network);

  public abstract void connectAndSetOwner(HQ_OkHttpClient client, HQ_Connection connection,
      HQ_HttpEngine owner, HQ_Request request) throws HQ_RouteException;

  public abstract void apply(HQ_ConnectionSpec tlsConfiguration, SSLSocket sslSocket,
      boolean isFallback);

  public abstract HQ_HttpUrl getHttpUrlChecked(String url)
      throws MalformedURLException, UnknownHostException;

  // TODO delete the following when web sockets move into the main package.
  public abstract void callEnqueue(HQ_Call call, HQ_Callback responseCallback, boolean forWebSocket);
  public abstract void callEngineReleaseConnection(HQ_Call call) throws IOException;
  public abstract HQ_Connection callEngineGetConnection(HQ_Call call);
  public abstract BufferedSource connectionRawSource(HQ_Connection connection);
  public abstract BufferedSink connectionRawSink(HQ_Connection connection);
  public abstract void connectionSetOwner(HQ_Connection connection, Object owner);
}
