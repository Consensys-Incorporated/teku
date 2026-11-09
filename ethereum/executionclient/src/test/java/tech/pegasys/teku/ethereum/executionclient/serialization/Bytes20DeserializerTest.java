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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;

class Bytes20DeserializerTest extends AbstractBytesDeserializerTest<Bytes20> {

  @Override
  AbstractBytesDeserializer<Bytes20> createDeserializer() {
    return new Bytes20Deserializer();
  }

  @Override
  String validHex() {
    // 20 bytes = 40 hex chars
    return "0x" + "01".repeat(20);
  }

  @Test
  void shouldDeserializeCorrectContent() throws IOException {
    String hex = "0x" + "01".repeat(20);
    Bytes20 result = deserialize(hex);
    assertThat(result).isEqualTo(Bytes20.fromHexString(hex));
  }

  @Test
  void shouldThrowOnWrongSize() {
    // 21 bytes is too long for Bytes20
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(21)))
        .isInstanceOf(IllegalArgumentException.class);
    // 19 bytes is too short for Bytes20
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(19)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
