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
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class PasswordUtilsTest {
  @Test
  void shouldLeaveSimplePasswordUnchanged() {
    assertThat(PasswordUtils.normalizePassword("testpassword")).isEqualTo(utf8("testpassword"));
  }

  @Test
  void shouldStripControlCharactersFromPassword() {
    assertThat(
            PasswordUtils.normalizePassword("\u0000\u001F\u0080\u009Ftest \n\f\tpass\u007Fword\n"))
        .isEqualTo(utf8("test password"));
  }

  @Test
  void shouldDecodePasswordFromEip2335() {
    final String password =
        "\uD835\uDD31\uD835\uDD22\uD835\uDD30\uD835\uDD31\uD835\uDD2D\uD835\uDD1E\uD835\uDD30\uD835\uDD30\uD835\uDD34\uD835\uDD2C\uD835\uDD2F\uD835\uDD21\uD83D\uDD11";
    assertThat(PasswordUtils.normalizePassword(password))
        .isEqualTo(Bytes.fromHexString("0x7465737470617373776f7264f09f9491"));
  }

  private Bytes utf8(final String password) {
    return Bytes.wrap(password.getBytes(UTF_8));
  }
}
