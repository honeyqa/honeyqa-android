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
package io.honeyqa.client.network.okhttp.internal.http;

import io.honeyqa.client.network.okhttp.HQ_Headers;
import io.honeyqa.client.network.okhttp.HQ_MediaType;
import io.honeyqa.client.network.okhttp.HQ_ResponseBody;
import io.honeyqa.client.network.okio.BufferedSource;

public final class HQ_RealResponseBody extends HQ_ResponseBody {
    private final HQ_Headers headers;
    private final BufferedSource source;

    public HQ_RealResponseBody(HQ_Headers headers, BufferedSource source) {
        this.headers = headers;
        this.source = source;
    }

    @Override
    public HQ_MediaType contentType() {
        String contentType = headers.get("Content-Type");
        return contentType != null ? HQ_MediaType.parse(contentType) : null;
    }

    @Override
    public long contentLength() {
        return HQ_OkHeaders.contentLength(headers);
    }

    @Override
    public BufferedSource source() {
        return source;
    }
}
