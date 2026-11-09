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

package tech.pegasys.teku.ethereum.executionclient.response;

import java.net.ConnectException;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;

public class ResponseUnwrapper {

  public static <K> K unwrapExecutionClientResponseOrThrow(final Response<K> response) {
    return unwrapResponseOrThrow(ExecutionType.EXECUTION_CLIENT, response);
  }

  public static <K> K unwrapBuilderResponseOrThrow(final Response<K> response) {
    return unwrapResponseOrThrow(ExecutionType.BUILDER, response);
  }

  public static <K> K unwrapResponseOrThrow(
      final ExecutionType executionType, final Response<K> response) {
    if (response.isFailure()) {
      final String errorMessage =
          String.format(
              "Invalid remote response from the %s: %s", executionType, response.errorMessage());
      if (response.errorMessage().contains("Failed to connect")) {
        throw new InvalidRemoteResponseException(
            errorMessage, new ConnectException(response.errorMessage()));
      }
      throw new InvalidRemoteResponseException(errorMessage);
    }
    return response.payload();
  }
}
