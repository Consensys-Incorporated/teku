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

package tech.pegasys.teku.ethereum.executionclient.serialization;

import static com.google.common.base.Preconditions.checkArgument;

public class QuantityChecker {

  /**
   * Applies additional checks on top of the Bytes.fromHexStringLenient to match the QUANTITY
   * pattern ^0x(?:0|(?:[a-fA-F1-9][a-fA-F0-9]*))$
   */
  public static void check(final String hexQuantity) {
    checkArgument(
        hexQuantity.startsWith("0x") && hexQuantity.length() >= 3,
        "Hex QUANTITY must start with 0x and can't be empty");
    checkArgument(
        hexQuantity.charAt(2) != '0' || hexQuantity.length() == 3,
        "Hex QUANTITY must not have leading zeros");
  }
}
