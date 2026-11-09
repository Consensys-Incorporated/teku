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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class BlockCheckpointsSerializerTest {

  public static final UInt64 JUSTIFIED_EPOCH = UInt64.valueOf(3232);
  public static final UInt64 FINALIZED_EPOCH = UInt64.valueOf(9382);
  private static final BlockCheckpoints VALUE =
      new BlockCheckpoints(
          new Checkpoint(JUSTIFIED_EPOCH, Bytes32.fromHexString("0x03")),
          new Checkpoint(FINALIZED_EPOCH, Bytes32.fromHexString("0x02")),
          new Checkpoint(UInt64.valueOf(3255), Bytes32.fromHexString("0x04")),
          new Checkpoint(UInt64.valueOf(3266), Bytes32.fromHexString("0x05")));

  /**
   * The original serialization format for CheckpointEpochs. This may be written to existing
   * databases so if you need to update it, backwards compatibility is likely to have been broken.
   */
  private static final Bytes ORIGINAL_SERIALIZATION =
      Bytes.fromHexString("0xa00c000000000000a624000000000000");

  @Test
  void shouldRoundTripCheckpointEpochs() {
    final byte[] data = CHECKPOINT_EPOCHS_SERIALIZER.serialize(VALUE);
    final BlockCheckpoints result = CHECKPOINT_EPOCHS_SERIALIZER.deserialize(data);
    assertThat(result).isEqualTo(VALUE);
  }

  @Test
  void shouldDecodeLegacyValue() {
    assertThat(CHECKPOINT_EPOCHS_SERIALIZER.deserialize(ORIGINAL_SERIALIZATION.toArrayUnsafe()))
        .isEqualTo(
            new BlockCheckpoints(
                new Checkpoint(JUSTIFIED_EPOCH, Bytes32.ZERO),
                new Checkpoint(FINALIZED_EPOCH, Bytes32.ZERO),
                new Checkpoint(JUSTIFIED_EPOCH, Bytes32.ZERO),
                new Checkpoint(FINALIZED_EPOCH, Bytes32.ZERO)));
  }
}
