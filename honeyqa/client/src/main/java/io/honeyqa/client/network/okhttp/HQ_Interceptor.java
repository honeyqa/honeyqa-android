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

import java.io.IOException;

/**
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * requests coming back in. Typically interceptors will be used to add, remove, or transform headers
 * on the request or response.
 */
public interface HQ_Interceptor {
  HQ_Response intercept(Chain chain) throws IOException;

  interface Chain {
    HQ_Request request();
    HQ_Response proceed(HQ_Request request) throws IOException;
    HQ_Connection connection();
  }
}
