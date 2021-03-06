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

import io.honeyqa.client.network.okhttp.internal.HQ_Util;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import io.honeyqa.client.network.okio.BufferedSink;
import io.honeyqa.client.network.okio.ByteString;
import io.honeyqa.client.network.okio.Okio;
import io.honeyqa.client.network.okio.Source;

public abstract class HQ_RequestBody {
  /** Returns the Content-Type header for this body. */
  public abstract HQ_MediaType contentType();

  /**
   * Returns the number of bytes that will be written to {@code out} in a call
   * to {@link #writeTo}, or -1 if that count is unknown.
   */
  public long contentLength() throws IOException {
    return -1;
  }

  /** Writes the content of this request to {@code out}. */
  public abstract void writeTo(BufferedSink sink) throws IOException;

  /**
   * Returns a new request body that transmits {@code content}. If {@code
   * contentType} is non-null and lacks a charset, this will use UTF-8.
   */
  public static HQ_RequestBody create(HQ_MediaType contentType, String content) {
    Charset charset = HQ_Util.UTF_8;
    if (contentType != null) {
      charset = contentType.charset();
      if (charset == null) {
        charset = HQ_Util.UTF_8;
        contentType = HQ_MediaType.parse(contentType + "; charset=utf-8");
      }
    }
    byte[] bytes = content.getBytes(charset);
    return create(contentType, bytes);
  }

  /** Returns a new request body that transmits {@code content}. */
  public static HQ_RequestBody create(final HQ_MediaType contentType, final ByteString content) {
    return new HQ_RequestBody() {
      @Override public HQ_MediaType contentType() {
        return contentType;
      }

      @Override public long contentLength() throws IOException {
        return content.size();
      }

      @Override public void writeTo(BufferedSink sink) throws IOException {
        sink.write(content);
      }
    };
  }

  /** Returns a new request body that transmits {@code content}. */
  public static HQ_RequestBody create(final HQ_MediaType contentType, final byte[] content) {
    return create(contentType, content, 0, content.length);
  }

  /** Returns a new request body that transmits {@code content}. */
  public static HQ_RequestBody create(final HQ_MediaType contentType, final byte[] content,
      final int offset, final int byteCount) {
    if (content == null) throw new NullPointerException("content == null");
    HQ_Util.checkOffsetAndCount(content.length, offset, byteCount);
    return new HQ_RequestBody() {
      @Override public HQ_MediaType contentType() {
        return contentType;
      }

      @Override public long contentLength() {
        return byteCount;
      }

      @Override public void writeTo(BufferedSink sink) throws IOException {
        sink.write(content, offset, byteCount);
      }
    };
  }

  /** Returns a new request body that transmits the content of {@code file}. */
  public static HQ_RequestBody create(final HQ_MediaType contentType, final File file) {
    if (file == null) throw new NullPointerException("content == null");

    return new HQ_RequestBody() {
      @Override public HQ_MediaType contentType() {
        return contentType;
      }

      @Override public long contentLength() {
        return file.length();
      }

      @Override public void writeTo(BufferedSink sink) throws IOException {
        Source source = null;
        try {
          source = Okio.source(file);
          sink.writeAll(source);
        } finally {
          HQ_Util.closeQuietly(source);
        }
      }
    };
  }
}
