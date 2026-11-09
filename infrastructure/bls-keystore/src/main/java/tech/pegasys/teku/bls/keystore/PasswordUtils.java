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

package tech.pegasys.teku.bls.keystore;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.apache.tuweni.bytes.Bytes;

public class PasswordUtils {

  public static Bytes normalizePassword(final String password) {
    final String normalizedPassword = Normalizer.normalize(password, Form.NFKD);
    final int[] filteredCodepoints =
        normalizedPassword.chars().filter(c -> !isControlCode(c)).toArray();
    final byte[] utf8Password =
        new String(filteredCodepoints, 0, filteredCodepoints.length).getBytes(UTF_8);
    return Bytes.wrap(utf8Password);
  }

  private static boolean isControlCode(final int c) {
    return isC0(c) || isC1(c) || c == 0x7F;
  }

  private static boolean isC1(final int c) {
    return 0x80 <= c && c <= 0x9F;
  }

  private static boolean isC0(final int c) {
    return 0x00 <= c && c <= 0x1F;
  }
}
