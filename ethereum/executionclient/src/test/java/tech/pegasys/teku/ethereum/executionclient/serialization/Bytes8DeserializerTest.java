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
import tech.pegasys.teku.infrastructure.bytes.Bytes8;

class Bytes8DeserializerTest extends AbstractBytesDeserializerTest<Bytes8> {

  @Override
  AbstractBytesDeserializer<Bytes8> createDeserializer() {
    return new Bytes8Deserializer();
  }

  @Override
  String validHex() {
    return "0x0102030405060708";
  }

  @Test
  void shouldDeserializeCorrectContent() throws IOException {
    Bytes8 result = deserialize("0x0102030405060708");
    assertThat(result).isEqualTo(Bytes8.fromHexString("0x0102030405060708"));
  }

  @Test
  void shouldThrowOnWrongSize() {
    // 9 bytes (18 hex chars) is too long for Bytes8
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(9)))
        .isInstanceOf(IllegalArgumentException.class);
    // 7 bytes (14 hex chars) is too short for Bytes8
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(7)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
