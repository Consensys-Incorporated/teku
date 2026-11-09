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

package tech.pegasys.teku.validator.remote.typedef;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.validator.api.SubmitDataError;

public record FailureListResponse(int code, String message, List<SubmitDataError> failures) {
  public static DeserializableTypeDefinition<FailureListResponse> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(
            FailureListResponse.class, FailureListResponseBuilder.class)
        .initializer(FailureListResponseBuilder::new)
        .finisher(FailureListResponseBuilder::build)
        .withField(
            "code", INTEGER_TYPE, FailureListResponse::code, FailureListResponseBuilder::code)
        .withField(
            "message",
            STRING_TYPE,
            FailureListResponse::message,
            FailureListResponseBuilder::message)
        .withField(
            "failures",
            DeserializableTypeDefinition.listOf(SubmitDataError.getJsonTypeDefinition()),
            FailureListResponse::failures,
            FailureListResponseBuilder::failures)
        .build();
  }

  public static ResponseHandler<FailureListResponse> getFailureListResponseResponseHandler() {
    return new ResponseHandler<>(FailureListResponse.getJsonTypeDefinition())
        .withHandler(SC_OK, FailureListResponse::empty)
        .withHandler(SC_BAD_REQUEST, FailureListResponse::onError);
  }

  private static Optional<FailureListResponse> empty(
      final Request request, final Response response) {
    return Optional.empty();
  }

  private static Optional<FailureListResponse> onError(
      final Request request, final Response response) {
    try {
      final ResponseBody responseBody = response.body();
      return Optional.of(
          JsonUtil.parse(responseBody.string(), FailureListResponse.getJsonTypeDefinition()));

    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to parse response body", ex);
    }
  }
}
