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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class BytesSerializerTest {

  private final BytesSerializer<Bytes32> serializer = new BytesSerializer<>(Bytes32::wrap);

  @Test
  public void roundTrip_zeroBytes() {
    final Bytes32 value = Bytes32.fromHexString("0x00");
    final byte[] bytes = serializer.serialize(value);
    final Bytes32 deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_maxValue() {
    final Bytes32 value =
        Bytes32.fromHexString("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    final byte[] bytes = serializer.serialize(value);
    final Bytes32 deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_other() {
    final Bytes32 value = Bytes32.fromHexString("0x123456");
    final byte[] bytes = serializer.serialize(value);
    final Bytes32 deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
