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

import java.util.Map;
import java.util.stream.Collectors;

public class FailoverRequestException extends RuntimeException {

  public FailoverRequestException(
      final String method, final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    super(createErrorMessage(method, capturedExceptions));
  }

  private static String createErrorMessage(
      final String method, final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    final String prefix =
        String.format(
            "Remote request (%s) failed on all configured Beacon Node endpoints%n", method);
    final String errorSummary =
        capturedExceptions.entrySet().stream()
            .map(entry -> entry.getKey().getEndpoint() + ": " + entry.getValue())
            .collect(Collectors.joining(System.lineSeparator()));
    return prefix + errorSummary;
  }
}
