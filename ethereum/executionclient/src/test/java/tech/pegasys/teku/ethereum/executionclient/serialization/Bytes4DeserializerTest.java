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
import tech.pegasys.teku.infrastructure.bytes.Bytes4;

class Bytes4DeserializerTest extends AbstractBytesDeserializerTest<Bytes4> {

  @Override
  AbstractBytesDeserializer<Bytes4> createDeserializer() {
    return new Bytes4Deserializer();
  }

  @Override
  String validHex() {
    return "0x01020304";
  }

  @Test
  void shouldDeserializeCorrectContent() throws IOException {
    Bytes4 result = deserialize("0x01020304");
    assertThat(result).isEqualTo(Bytes4.fromHexString("0x01020304"));
  }

  @Test
  void shouldThrowOnWrongSize() {
    // 5 bytes (10 hex chars) is too long for Bytes4
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(5)))
        .isInstanceOf(IllegalArgumentException.class);
    // 3 bytes (6 hex chars) is too short for Bytes4
    assertThatThrownBy(() -> deserialize("0x" + "00".repeat(3)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
