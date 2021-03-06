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

import io.honeyqa.client.network.okhttp.HQ_Headers;
import io.honeyqa.client.network.okhttp.HQ_Protocol;
import io.honeyqa.client.network.okhttp.HQ_Request;
import io.honeyqa.client.network.okhttp.HQ_Response;
import io.honeyqa.client.network.okhttp.HQ_ResponseBody;
import io.honeyqa.client.network.okhttp.internal.HQ_Util;
import io.honeyqa.client.network.okhttp.internal.framed.HQ_ErrorCode;
import io.honeyqa.client.network.okhttp.internal.framed.HQ_FramedConnection;
import io.honeyqa.client.network.okhttp.internal.framed.HQ_FramedStream;
import io.honeyqa.client.network.okhttp.internal.framed.HQ_Header;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import io.honeyqa.client.network.okio.ByteString;
import io.honeyqa.client.network.okio.Okio;
import io.honeyqa.client.network.okio.Sink;

import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.RESPONSE_STATUS;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.TARGET_AUTHORITY;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.TARGET_HOST;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.TARGET_METHOD;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.TARGET_PATH;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.TARGET_SCHEME;
import static io.honeyqa.client.network.okhttp.internal.framed.HQ_Header.VERSION;

public final class HQ_FramedTransport implements HQ_Transport {
  /** See http://www.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3-1#TOC-3.2.1-Request. */
  private static final List<ByteString> SPDY_3_PROHIBITED_HEADERS = HQ_Util.immutableList(
          ByteString.encodeUtf8("connection"),
          ByteString.encodeUtf8("host"),
          ByteString.encodeUtf8("keep-alive"),
          ByteString.encodeUtf8("proxy-connection"),
          ByteString.encodeUtf8("transfer-encoding"));

  /** See http://tools.ietf.org/html/draft-ietf-httpbis-http2-09#section-8.1.3. */
  private static final List<ByteString> HTTP_2_PROHIBITED_HEADERS = HQ_Util.immutableList(
          ByteString.encodeUtf8("connection"),
          ByteString.encodeUtf8("host"),
          ByteString.encodeUtf8("keep-alive"),
          ByteString.encodeUtf8("proxy-connection"),
          ByteString.encodeUtf8("te"),
          ByteString.encodeUtf8("transfer-encoding"),
          ByteString.encodeUtf8("encoding"),
          ByteString.encodeUtf8("upgrade"));

  private final HQ_HttpEngine httpEngine;
  private final HQ_FramedConnection framedConnection;
  private HQ_FramedStream stream;

  public HQ_FramedTransport(HQ_HttpEngine httpEngine, HQ_FramedConnection framedConnection) {
    this.httpEngine = httpEngine;
    this.framedConnection = framedConnection;
  }

  @Override public Sink createRequestBody(HQ_Request request, long contentLength) throws IOException {
    return stream.getSink();
  }

  @Override public void writeRequestHeaders(HQ_Request request) throws IOException {
    if (stream != null) return;

    httpEngine.writingRequestHeaders();
    boolean permitsRequestBody = httpEngine.permitsRequestBody(request);
    boolean hasResponseBody = true;
    String version = HQ_RequestLine.version(httpEngine.getConnection().getProtocol());
    stream = framedConnection.newStream(
        writeNameValueBlock(request, framedConnection.getProtocol(), version), permitsRequestBody,
        hasResponseBody);
    stream.readTimeout().timeout(httpEngine.client.getReadTimeout(), TimeUnit.MILLISECONDS);
  }

  @Override public void writeRequestBody(HQ_RetryableSink requestBody) throws IOException {
    requestBody.writeToSocket(stream.getSink());
  }

  @Override public void finishRequest() throws IOException {
    stream.getSink().close();
  }

  @Override public HQ_Response.Builder readResponseHeaders() throws IOException {
    return readNameValueBlock(stream.getResponseHeaders(), framedConnection.getProtocol());
  }

  /**
   * Returns a list of alternating names and values containing a SPDY request.
   * Names are all lowercase. No names are repeated. If any name has multiple
   * values, they are concatenated using "\0" as a delimiter.
   */
  public static List<HQ_Header> writeNameValueBlock(HQ_Request request, HQ_Protocol protocol,
      String version) {
    HQ_Headers headers = request.headers();
    List<HQ_Header> result = new ArrayList<>(headers.size() + 10);
    result.add(new HQ_Header(TARGET_METHOD, request.method()));
    result.add(new HQ_Header(TARGET_PATH, HQ_RequestLine.requestPath(request.httpUrl())));
    String host = HQ_Util.hostHeader(request.httpUrl());
    if (HQ_Protocol.SPDY_3 == protocol) {
      result.add(new HQ_Header(VERSION, version));
      result.add(new HQ_Header(TARGET_HOST, host));
    } else if (HQ_Protocol.HTTP_2 == protocol) {
      result.add(new HQ_Header(TARGET_AUTHORITY, host)); // Optional in HTTP/2
    } else {
      throw new AssertionError();
    }
    result.add(new HQ_Header(TARGET_SCHEME, request.httpUrl().scheme()));

    Set<ByteString> names = new LinkedHashSet<ByteString>();
    for (int i = 0, size = headers.size(); i < size; i++) {
      // header names must be lowercase.
      ByteString name = ByteString.encodeUtf8(headers.name(i).toLowerCase(Locale.US));
      String value = headers.value(i);

      // Drop headers that are forbidden when layering HTTP over SPDY.
      if (isProhibitedHeader(protocol, name)) continue;

      // They shouldn't be set, but if they are, drop them. We've already written them!
      if (name.equals(TARGET_METHOD)
          || name.equals(TARGET_PATH)
          || name.equals(TARGET_SCHEME)
          || name.equals(TARGET_AUTHORITY)
          || name.equals(TARGET_HOST)
          || name.equals(VERSION)) {
        continue;
      }

      // If we haven't seen this name before, add the pair to the end of the list...
      if (names.add(name)) {
        result.add(new HQ_Header(name, value));
        continue;
      }

      // ...otherwise concatenate the existing values and this value.
      for (int j = 0; j < result.size(); j++) {
        if (result.get(j).name.equals(name)) {
          String concatenated = joinOnNull(result.get(j).value.utf8(), value);
          result.set(j, new HQ_Header(name, concatenated));
          break;
        }
      }
    }
    return result;
  }

  private static String joinOnNull(String first, String second) {
    return new StringBuilder(first).append('\0').append(second).toString();
  }

  /** Returns headers for a name value block containing a SPDY response. */
  public static HQ_Response.Builder readNameValueBlock(List<HQ_Header> headerBlock,
      HQ_Protocol protocol) throws IOException {
    String status = null;
    String version = "HTTP/1.1"; // :version present only in spdy/3.

    HQ_Headers.Builder headersBuilder = new HQ_Headers.Builder();
    headersBuilder.set(HQ_OkHeaders.SELECTED_PROTOCOL, protocol.toString());
    for (int i = 0, size = headerBlock.size(); i < size; i++) {
      ByteString name = headerBlock.get(i).name;
      String values = headerBlock.get(i).value.utf8();
      for (int start = 0; start < values.length(); ) {
        int end = values.indexOf('\0', start);
        if (end == -1) {
          end = values.length();
        }
        String value = values.substring(start, end);
        if (name.equals(RESPONSE_STATUS)) {
          status = value;
        } else if (name.equals(VERSION)) {
          version = value;
        } else if (!isProhibitedHeader(protocol, name)) { // Don't write forbidden headers!
          headersBuilder.add(name.utf8(), value);
        }
        start = end + 1;
      }
    }
    if (status == null) throw new ProtocolException("Expected ':status' header not present");

    HQ_StatusLine statusLine = HQ_StatusLine.parse(version + " " + status);
    return new HQ_Response.Builder()
        .protocol(protocol)
        .code(statusLine.code)
        .message(statusLine.message)
        .headers(headersBuilder.build());
  }

  @Override public HQ_ResponseBody openResponseBody(HQ_Response response) throws IOException {
    return new HQ_RealResponseBody(response.headers(), Okio.buffer(stream.getSource()));
  }

  @Override public void releaseConnectionOnIdle() {
  }

  @Override public void disconnect(HQ_HttpEngine engine) throws IOException {
    if (stream != null) stream.close(HQ_ErrorCode.CANCEL);
  }

  @Override public boolean canReuseConnection() {
    return true; // TODO: framedConnection.isClosed() ?
  }

  /** When true, this header should not be emitted or consumed. */
  private static boolean isProhibitedHeader(HQ_Protocol protocol, ByteString name) {
    if (protocol == HQ_Protocol.SPDY_3) {
      return SPDY_3_PROHIBITED_HEADERS.contains(name);
    } else if (protocol == HQ_Protocol.HTTP_2) {
      return HTTP_2_PROHIBITED_HEADERS.contains(name);
    } else {
      throw new AssertionError(protocol);
    }
  }
}
