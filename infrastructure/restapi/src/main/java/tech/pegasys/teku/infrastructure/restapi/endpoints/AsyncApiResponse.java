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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;

public class AsyncApiResponse {
  final int responseCode;
  final Optional<Object> responseBody;

  private AsyncApiResponse(final int responseCode, final Object responseBody) {
    this.responseCode = responseCode;
    this.responseBody = Optional.ofNullable(responseBody);
  }

  public int getResponseCode() {
    return responseCode;
  }

  public Optional<Object> getResponseBody() {
    return responseBody;
  }

  /**
   * Respond with error.
   *
   * <p>Errors must be defined with `CoreTypes.HTTP_ERROR_RESPONSE_TYPE`
   *
   * @param responseCode HTTP error code
   * @param errorMessage Message text for response to user
   * @return AsyncApiResponse with a HttpErrorResponse as the responseBody
   */
  public static AsyncApiResponse respondWithError(
      final int responseCode, final String errorMessage) {
    return new AsyncApiResponse(responseCode, new HttpErrorResponse(responseCode, errorMessage));
  }

  public static AsyncApiResponse respondWithCode(final int responseCode) {
    return new AsyncApiResponse(responseCode, null);
  }

  public static AsyncApiResponse respondOk(final Object responseBody) {
    return new AsyncApiResponse(SC_OK, responseBody);
  }

  public static AsyncApiResponse respondWithObject(
      final int responseCode, final Object responseBody) {
    return new AsyncApiResponse(responseCode, responseBody);
  }

  public static AsyncApiResponse respondNotFound() {
    return new AsyncApiResponse(SC_NOT_FOUND, new HttpErrorResponse(SC_NOT_FOUND, "Not found"));
  }

  public static AsyncApiResponse respondServiceUnavailable() {
    return new AsyncApiResponse(
        SC_SERVICE_UNAVAILABLE,
        new HttpErrorResponse(
            SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing and not serving requests."));
  }
}
