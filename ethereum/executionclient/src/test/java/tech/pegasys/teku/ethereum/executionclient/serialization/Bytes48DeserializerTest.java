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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class Bytes48DeserializerTest extends AbstractBytesDeserializerTest<Bytes48> {

  @Override
  AbstractBytesDeserializer<Bytes48> createDeserializer() {
    return new Bytes48Deserializer();
  }

  @Override
  String validHex() {
    // 48 bytes = 96 hex chars
    return "0x" + "cd".repeat(48);
  }

  @Test
  void shouldDeserializeCorrectContent() throws IOException {
    String hex = "0x" + "cd".repeat(48);
    Bytes48 result = deserialize(hex);
    assertThat(result).isEqualTo(Bytes48.fromHexStringStrict(hex));
  }

  @Test
  void shouldThrowOnWrongSize() {
    // 49 bytes is too long for Bytes48
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(49)))
        .isInstanceOf(IllegalArgumentException.class);
    // 47 bytes is too short for Bytes48
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(47)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
