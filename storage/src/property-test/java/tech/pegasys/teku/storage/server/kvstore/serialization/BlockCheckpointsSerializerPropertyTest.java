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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.CHECKPOINT_EPOCHS_SERIALIZER;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class BlockCheckpointsSerializerPropertyTest {
  @Property
  void roundTrip(
      @ForAll("checkpoints") final Checkpoint justifiedCheckpoint,
      @ForAll("checkpoints") final Checkpoint finalizedCheckpoint,
      @ForAll("checkpoints") final Checkpoint unrealizedJustifiedCheckpoint,
      @ForAll("checkpoints") final Checkpoint unrealizedFinalizedCheckpoint) {
    final BlockCheckpoints value =
        new BlockCheckpoints(
            justifiedCheckpoint,
            finalizedCheckpoint,
            unrealizedJustifiedCheckpoint,
            unrealizedFinalizedCheckpoint);
    final byte[] serialized = CHECKPOINT_EPOCHS_SERIALIZER.serialize(value);
    final BlockCheckpoints deserialized = CHECKPOINT_EPOCHS_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }

  @Provide
  Arbitrary<Checkpoint> checkpoints() {
    return Combinators.combine(uint64(), bytes32()).as(Checkpoint::new);
  }

  @Provide
  Arbitrary<Bytes32> bytes32() {
    return Arbitraries.bytes().array(byte[].class).ofSize(32).map(Bytes32::wrap);
  }

  @Provide
  Arbitrary<UInt64> uint64() {
    return Arbitraries.longs().map(UInt64::fromLongBits);
  }
}
