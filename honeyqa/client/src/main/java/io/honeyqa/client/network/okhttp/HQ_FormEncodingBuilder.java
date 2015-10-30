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

import io.honeyqa.client.network.okio.Buffer;

/**
 * Fluent API to build <a href="http://www.w3.org/MarkUp/html-spec/html-spec_8.html#SEC8.2.1">HTML
 * 2.0</a>-compliant form data.
 */
public final class HQ_FormEncodingBuilder {
  private static final HQ_MediaType CONTENT_TYPE =
      HQ_MediaType.parse("application/x-www-form-urlencoded");

  private final Buffer content = new Buffer();

  /** Add new key-value pair. */
  public HQ_FormEncodingBuilder add(String name, String value) {
    if (content.size() > 0) {
      content.writeByte('&');
    }
    HQ_HttpUrl.canonicalize(content, name, 0, name.length(),
            HQ_HttpUrl.FORM_ENCODE_SET, false, true);
    content.writeByte('=');
    HQ_HttpUrl.canonicalize(content, value, 0, value.length(),
            HQ_HttpUrl.FORM_ENCODE_SET, false, true);
    return this;
  }

  /** Add new key-value pair. */
  public HQ_FormEncodingBuilder addEncoded(String name, String value) {
    if (content.size() > 0) {
      content.writeByte('&');
    }
    HQ_HttpUrl.canonicalize(content, name, 0, name.length(),
            HQ_HttpUrl.FORM_ENCODE_SET, true, true);
    content.writeByte('=');
    HQ_HttpUrl.canonicalize(content, value, 0, value.length(),
            HQ_HttpUrl.FORM_ENCODE_SET, true, true);
    return this;
  }

  public HQ_RequestBody build() {
    return HQ_RequestBody.create(CONTENT_TYPE, content.snapshot());
  }
}
