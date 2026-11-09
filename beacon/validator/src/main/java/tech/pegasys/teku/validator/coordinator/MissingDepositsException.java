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

package tech.pegasys.teku.validator.coordinator;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MissingDepositsException extends IllegalStateException {

  private MissingDepositsException(final String message) {
    super(message);
  }

  public static MissingDepositsException missingRange(
      final UInt64 fromIndex, final UInt64 toIndex) {
    final String errorMessage =
        "Unable to create block because ETH1 deposits are not available. Missing deposits "
            + fromIndex
            + " to "
            + toIndex;
    return new MissingDepositsException(errorMessage);
  }
}
