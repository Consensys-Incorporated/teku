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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MinGenesisTimeBlockEventSerializerPropertyTest {
  @Property
  void roundTrip(
      @ForAll final long timestamp,
      @ForAll final long blockNumber,
      @ForAll @Size(32) final byte[] blockHash) {
    final MinGenesisTimeBlockEvent value =
        new MinGenesisTimeBlockEvent(
            UInt64.fromLongBits(timestamp),
            UInt64.fromLongBits(blockNumber),
            Bytes32.wrap(blockHash));
    final byte[] serialized = MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER.serialize(value);
    final MinGenesisTimeBlockEvent deserialized =
        MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).usingDefaultComparator().isEqualTo(value);
  }
}
