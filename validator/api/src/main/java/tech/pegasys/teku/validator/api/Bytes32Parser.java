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

package tech.pegasys.teku.validator.api;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.tuweni.bytes.Bytes32;

public class Bytes32Parser {

  public static Bytes32 toBytes32(final String input) {
    return toBytes32(input.getBytes(UTF_8));
  }

  public static Bytes32 toBytes32(final byte[] input) {
    if (input.length > 32) {
      throw new IllegalArgumentException(
          "'"
              + new String(input, UTF_8)
              + "' converts to "
              + input.length
              + " bytes. Input must be 32 bytes or less.");
    }
    byte[] bytes = new byte[32];
    System.arraycopy(input, 0, bytes, 0, input.length);
    return Bytes32.wrap(bytes);
  }
}
