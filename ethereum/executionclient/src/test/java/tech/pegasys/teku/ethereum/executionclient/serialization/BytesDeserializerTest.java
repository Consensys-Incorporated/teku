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

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BytesDeserializerTest extends AbstractBytesDeserializerTest<Bytes> {

  @Override
  AbstractBytesDeserializer<Bytes> createDeserializer() {
    return new BytesDeserializer();
  }

  @Override
  String validHex() {
    // 16 bytes — arbitrary length is valid for Bytes
    return "0x" + "0a".repeat(16);
  }

  @Test
  void shouldDeserializeEmptyBytes() throws IOException {
    assertThat(deserialize("0x")).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldDeserializeCorrectContent() throws IOException {
    Bytes result = deserialize("0xdeadbeef");
    assertThat(result).isEqualTo(Bytes.fromHexString("0xdeadbeef"));
  }

  @Test
  void shouldDeserializeVariableLengths() throws IOException {
    assertThat(deserialize("0x01")).isEqualTo(Bytes.of(0x01));
    assertThat(deserialize("0x0102")).isEqualTo(Bytes.of(0x01, 0x02));
    assertThat(deserialize("0x" + "ff".repeat(32))).hasToString("0x" + "ff".repeat(32));
  }
}
