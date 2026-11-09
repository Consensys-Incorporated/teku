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

package tech.pegasys.teku.ethereum.pow.api;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class InvalidDepositEventsException extends RuntimeException {

  public InvalidDepositEventsException(final String s) {
    super(s);
  }

  public static InvalidDepositEventsException expectedDepositAtIndex(
      final UInt64 expectedIndex, final UInt64 actualIndex) {
    return new InvalidDepositEventsException(
        "Expected next deposit at index " + expectedIndex + ", but got " + actualIndex);
  }
}
