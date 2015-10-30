/*
 * Copyright (C) 2011 The Android Open Source Project
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

package io.honeyqa.client.network.okhttp.internal.framed;

import java.io.IOException;

/** Listener to be notified when a connected peer creates a new stream. */
public interface HQ_IncomingStreamHandler {
  HQ_IncomingStreamHandler REFUSE_INCOMING_STREAMS = new HQ_IncomingStreamHandler() {
    @Override public void receive(HQ_FramedStream stream) throws IOException {
      stream.close(HQ_ErrorCode.REFUSED_STREAM);
    }
  };

  /**
   * Handle a new stream from this connection's peer. Implementations should
   * respond by either {@link HQ_FramedStream#reply replying to the stream} or
   * {@link HQ_FramedStream#close closing it}. This response does not need to be
   * synchronous.
   */
  void receive(HQ_FramedStream stream) throws IOException;
}
