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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class SlotAndBlockRootAndBlobIndexKeySerializerTest {

  private static final UInt64 EXPECTED_SLOT = UInt64.valueOf(1234589);
  private static final Bytes32 EXPECTED_ROOT =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final UInt64 EXPECTED_INDEX = UInt64.valueOf(2);

  private final SlotAndBlockRootAndBlobIndexKeySerializer serializer =
      new SlotAndBlockRootAndBlobIndexKeySerializer();

  @Test
  public void roundTrip() {
    final SlotAndBlockRootAndBlobIndex expected =
        new SlotAndBlockRootAndBlobIndex(EXPECTED_SLOT, EXPECTED_ROOT, EXPECTED_INDEX);
    final byte[] data = serializer.serialize(expected);
    final SlotAndBlockRootAndBlobIndex deserialized = serializer.deserialize(data);
    assertThat(deserialized).isEqualTo(expected);
  }
}
