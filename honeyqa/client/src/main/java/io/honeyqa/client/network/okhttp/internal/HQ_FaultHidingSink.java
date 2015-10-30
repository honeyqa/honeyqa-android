package io.honeyqa.client.network.okhttp.internal;

import java.io.IOException;
import io.honeyqa.client.network.okio.Buffer;
import io.honeyqa.client.network.okio.ForwardingSink;
import io.honeyqa.client.network.okio.Sink;

/** A sink that never throws IOExceptions, even if the underlying sink does. */
class HQ_FaultHidingSink extends ForwardingSink {
  private boolean hasErrors;

  public HQ_FaultHidingSink(Sink delegate) {
    super(delegate);
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    if (hasErrors) {
      source.skip(byteCount);
      return;
    }
    try {
      super.write(source, byteCount);
    } catch (IOException e) {
      hasErrors = true;
      onException(e);
    }
  }

  @Override public void flush() throws IOException {
    if (hasErrors) return;
    try {
      super.flush();
    } catch (IOException e) {
      hasErrors = true;
      onException(e);
    }
  }

  @Override public void close() throws IOException {
    if (hasErrors) return;
    try {
      super.close();
    } catch (IOException e) {
      hasErrors = true;
      onException(e);
    }
  }

  protected void onException(IOException e) {
  }
}
