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

package tech.pegasys.teku.validator.remote;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

final class RestResponseSizeLimitInterceptor implements Interceptor {
  private static final int MB = 1_000_000;

  private final long maxBytes;

  RestResponseSizeLimitInterceptor(final int maxMegabytes) {
    this.maxBytes = maxMegabytes * (long) MB;
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Response response = chain.proceed(chain.request());
    final ResponseBody body = response.body();
    if (body == null) {
      return response;
    }
    if (body.contentLength() > maxBytes) {
      response.close();
      throw sizeLimitExceeded();
    }

    final BufferedSource limitedSource =
        Okio.buffer(
            new ForwardingSource(body.source()) {
              private long bytesRead;

              @Override
              public long read(final Buffer sink, final long byteCount) throws IOException {
                final long read = super.read(sink, Math.min(byteCount, maxBytes - bytesRead + 1));
                if (read > 0) {
                  bytesRead += read;
                  if (bytesRead > maxBytes) {
                    throw sizeLimitExceeded();
                  }
                }
                return read;
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

  private IOException sizeLimitExceeded() {
    return new IOException("Beacon Node API response exceeds " + (maxBytes / MB) + " MB");
  }
}
