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

package tech.pegasys.teku.infrastructure.http;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;

import java.util.Objects;

public class HttpErrorResponse {

  private final int code;
  private final String message;

  public static HttpErrorResponse badRequest(final String message) {
    return new HttpErrorResponse(SC_BAD_REQUEST, message);
  }

  public static HttpErrorResponse serviceUnavailable() {
    return new HttpErrorResponse(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
  }

  public static HttpErrorResponse noContent() {
    return new HttpErrorResponse(SC_NO_CONTENT, "");
  }

  public HttpErrorResponse(final int code, final String message) {
    this.code = code;
    this.message = message;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getMessage() {
    return message;
  }

  public int getCode() {
    return code;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HttpErrorResponse that = (HttpErrorResponse) o;
    return code == that.code && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message);
  }

  @Override
  public String toString() {
    return "HttpErrorResponse{" + "code=" + code + ", message='" + message + '\'' + '}';
  }

  public static class Builder {
    private int code;
    private String message;

    Builder() {}

    public Builder code(final int code) {
      this.code = code;
      return this;
    }

    public Builder message(final String message) {
      this.message = message;
      return this;
    }

    public HttpErrorResponse build() {
      return new HttpErrorResponse(code, message);
    }
  }
}
