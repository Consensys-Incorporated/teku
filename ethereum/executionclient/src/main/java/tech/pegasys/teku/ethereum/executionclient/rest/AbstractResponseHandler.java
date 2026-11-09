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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

import java.io.IOException;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public abstract class AbstractResponseHandler {
  private static final Logger LOG = LogManager.getLogger();

  abstract void handleFailure(final IOException exception);

  abstract void handleResponse(final Request request, final okhttp3.Response response);

  protected <T> boolean handleResponseError(
      final Request request,
      final okhttp3.Response response,
      final SafeFuture<Response<T>> futureResponse) {
    LOG.trace("{} {} {}", request.method(), request.url(), response.code());
    if (response.isSuccessful()) {
      return false;
    }
    try (response) {
      if (response.code() == SC_UNSUPPORTED_MEDIA_TYPE) {
        futureResponse.complete(Response.fromUnsupportedMediaTypeError());
      } else {
        final String errorMessage = getErrorMessageForFailedResponse(response);
        futureResponse.complete(Response.fromErrorMessage(errorMessage));
      }
    } catch (final Throwable ex) {
      futureResponse.completeExceptionally(ex);
    }
    return true;
  }

  private String getErrorMessageForFailedResponse(final okhttp3.Response response)
      throws IOException {
    try (final ResponseBody responseBody = response.body()) {
      if (bodyIsEmpty(responseBody)) {
        return response.code() + ": " + response.message();
      }
      return responseBody.string();
    }
  }

  protected boolean bodyIsEmpty(final ResponseBody responseBody) {
    return responseBody == null || responseBody.contentLength() == 0;
  }
}
