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
package io.honeyqa.client.network.okhttp.internal;

import io.honeyqa.client.network.okhttp.HQ_Route;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A blacklist of failed routes to avoid when creating a new connection to a
 * target address. This is used so that OkHttp can learn from its mistakes: if
 * there was a failure attempting to connect to a specific IP address or proxy
 * server, that failure is remembered and alternate routes are preferred.
 */
public final class HQ_RouteDatabase {
  private final Set<HQ_Route> failedRoutes = new LinkedHashSet<>();

  /** Records a failure connecting to {@code failedRoute}. */
  public synchronized void failed(HQ_Route failedRoute) {
    failedRoutes.add(failedRoute);
  }

  /** Records success connecting to {@code failedRoute}. */
  public synchronized void connected(HQ_Route route) {
    failedRoutes.remove(route);
  }

  /** Returns true if {@code route} has failed recently and should be avoided. */
  public synchronized boolean shouldPostpone(HQ_Route route) {
    return failedRoutes.contains(route);
  }

  public synchronized int failedRoutesCount() {
    return failedRoutes.size();
  }
}
