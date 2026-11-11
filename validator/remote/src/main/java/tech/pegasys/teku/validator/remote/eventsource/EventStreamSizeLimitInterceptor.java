/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.validator.remote.eventsource;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.ForwardingSource;
import okio.Okio;

/** Bounds each SSE frame before okhttp-eventsource can buffer its field values. */
public class EventStreamSizeLimitInterceptor implements Interceptor {

  static final int BYTES_PER_MEGABYTE = 1_000_000;
  // Cap temporary read-ahead to 8 KiB; maxFrameBytes below is the separate per-event limit.
  private static final int READ_CHUNK_SIZE = 8192;
  private static final ByteString CR_OR_LF = ByteString.of((byte) '\r', (byte) '\n');

  private final int maxEventSizeMegabytes;

  public EventStreamSizeLimitInterceptor(final int maxEventSizeMegabytes) {
    this.maxEventSizeMegabytes = maxEventSizeMegabytes;
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Response response = chain.proceed(chain.request());
    final ResponseBody body = response.body();
    if (body == null) {
      return response;
    }

    final BufferedSource limitedSource =
        Okio.buffer(
            new ForwardingSource(body.source()) {
              private final long maxFrameBytes = (long) maxEventSizeMegabytes * BYTES_PER_MEGABYTE;
              private final Buffer chunk = new Buffer();
              private long frameBytes;
              private boolean lineHasContent;
              private boolean afterCarriageReturn;
              private boolean blankLineAtCarriageReturn;

              @Override
              public long read(final Buffer sink, final long byteCount) throws IOException {
                if (byteCount == 0) {
                  return 0;
                }
                final long bytesRead = super.read(chunk, Math.min(byteCount, READ_CHUNK_SIZE));
                long offset = 0;
                while (offset < bytesRead) {
                  final long lineEnding = chunk.indexOfElement(CR_OR_LF, offset);
                  final long contentEnd = lineEnding == -1 ? bytesRead : lineEnding;
                  if (contentEnd > offset) {
                    frameBytes += contentEnd - offset;
                    if (frameBytes > maxFrameBytes) {
                      throw new EventStreamSizeLimitException(maxEventSizeMegabytes);
                    }
                    lineHasContent = true;
                    afterCarriageReturn = false;
                  }
                  if (lineEnding == -1) {
                    break;
                  }
                  final byte value = chunk.getByte(lineEnding);
                  frameBytes++;
                  if (frameBytes > maxFrameBytes) {
                    throw new EventStreamSizeLimitException(maxEventSizeMegabytes);
                  }
                  if (afterCarriageReturn && value == '\n') {
                    if (blankLineAtCarriageReturn) {
                      frameBytes = 0;
                    }
                    afterCarriageReturn = false;
                  } else {
                    afterCarriageReturn = false;
                    final boolean blankLine = !lineHasContent;
                    if (blankLine) {
                      frameBytes = 0;
                    }
                    lineHasContent = false;
                    afterCarriageReturn = value == '\r';
                    blankLineAtCarriageReturn = afterCarriageReturn && blankLine;
                  }
                  offset = lineEnding + 1;
                }
                if (bytesRead > 0) {
                  sink.write(chunk, bytesRead);
                }
                return bytesRead;
              }
            });
    return response
        .newBuilder()
        .body(
            new ResponseBody() {
              @Override
              public MediaType contentType() {
                return body.contentType();
              }

              @Override
              public long contentLength() {
                return body.contentLength();
              }

              @Override
              public BufferedSource source() {
                return limitedSource;
              }
            })
        .build();
  }
}
