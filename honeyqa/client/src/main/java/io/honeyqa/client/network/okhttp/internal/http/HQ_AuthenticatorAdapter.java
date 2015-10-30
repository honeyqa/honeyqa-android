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
package io.honeyqa.client.network.okhttp.internal.http;

import io.honeyqa.client.network.okhttp.HQ_Authenticator;
import io.honeyqa.client.network.okhttp.HQ_Challenge;
import io.honeyqa.client.network.okhttp.HQ_Credentials;
import io.honeyqa.client.network.okhttp.HQ_HttpUrl;
import io.honeyqa.client.network.okhttp.HQ_Request;
import io.honeyqa.client.network.okhttp.HQ_Response;

import java.io.IOException;
import java.net.Authenticator.RequestorType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.List;

/** Adapts {@link java.net.Authenticator} to {@link HQ_Authenticator}. */
public final class HQ_AuthenticatorAdapter implements HQ_Authenticator {
  /** Uses the global authenticator to get the password. */
  public static final HQ_Authenticator INSTANCE = new HQ_AuthenticatorAdapter();

  @Override public HQ_Request authenticate(Proxy proxy, HQ_Response response) throws IOException {
    List<HQ_Challenge> challenges = response.challenges();
    HQ_Request request = response.request();
    HQ_HttpUrl url = request.httpUrl();
    for (int i = 0, size = challenges.size(); i < size; i++) {
      HQ_Challenge challenge = challenges.get(i);
      if (!"Basic".equalsIgnoreCase(challenge.getScheme())) continue;

      PasswordAuthentication auth = java.net.Authenticator.requestPasswordAuthentication(
          url.host(), getConnectToInetAddress(proxy, url), url.port(), url.scheme(),
          challenge.getRealm(), challenge.getScheme(), url.url(), RequestorType.SERVER);
      if (auth == null) continue;

      String credential = HQ_Credentials.basic(auth.getUserName(), new String(auth.getPassword()));
      return request.newBuilder()
          .header("Authorization", credential)
          .build();
    }
    return null;

  }

  @Override public HQ_Request authenticateProxy(Proxy proxy, HQ_Response response) throws IOException {
    List<HQ_Challenge> challenges = response.challenges();
    HQ_Request request = response.request();
    HQ_HttpUrl url = request.httpUrl();
    for (int i = 0, size = challenges.size(); i < size; i++) {
      HQ_Challenge challenge = challenges.get(i);
      if (!"Basic".equalsIgnoreCase(challenge.getScheme())) continue;

      InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
      PasswordAuthentication auth = java.net.Authenticator.requestPasswordAuthentication(
          proxyAddress.getHostName(), getConnectToInetAddress(proxy, url), proxyAddress.getPort(),
          url.scheme(), challenge.getRealm(), challenge.getScheme(), url.url(),
          RequestorType.PROXY);
      if (auth == null) continue;

      String credential = HQ_Credentials.basic(auth.getUserName(), new String(auth.getPassword()));
      return request.newBuilder()
          .header("Proxy-Authorization", credential)
          .build();
    }
    return null;
  }

  private InetAddress getConnectToInetAddress(Proxy proxy, HQ_HttpUrl url) throws IOException {
    return (proxy != null && proxy.type() != Proxy.Type.DIRECT)
        ? ((InetSocketAddress) proxy.address()).getAddress()
        : InetAddress.getByName(url.host());
  }
}
