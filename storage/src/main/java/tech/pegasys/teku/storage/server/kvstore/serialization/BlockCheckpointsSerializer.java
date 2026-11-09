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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class BlockCheckpointsSerializer implements KvStoreSerializer<BlockCheckpoints> {

  @Override
  public BlockCheckpoints deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UInt64 justifiedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final UInt64 finalizedEpoch = UInt64.fromLongBits(reader.readUInt64());
          if (reader.isComplete()) {
            final Checkpoint justifiedCheckpoint = new Checkpoint(justifiedEpoch, Bytes32.ZERO);
            final Checkpoint finalizedCheckpoint = new Checkpoint(finalizedEpoch, Bytes32.ZERO);
            return new BlockCheckpoints(
                justifiedCheckpoint, finalizedCheckpoint, justifiedCheckpoint, finalizedCheckpoint);
          }
          final UInt64 unrealizedJustifiedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final UInt64 unrealizedFinalizedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final Bytes32 justifiedRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 finalizedRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 unrealizedJustifiedRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 unrealizedFinalizedRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          return new BlockCheckpoints(
              new Checkpoint(justifiedEpoch, justifiedRoot),
              new Checkpoint(finalizedEpoch, finalizedRoot),
              new Checkpoint(unrealizedJustifiedEpoch, unrealizedJustifiedRoot),
              new Checkpoint(unrealizedFinalizedEpoch, unrealizedFinalizedRoot));
        });
  }

  @Override
  public byte[] serialize(final BlockCheckpoints value) {
    return SSZ.encode(
            writer -> {
              writer.writeUInt64(value.getJustifiedCheckpoint().getEpoch().longValue());
              writer.writeUInt64(value.getFinalizedCheckpoint().getEpoch().longValue());
              writer.writeUInt64(value.getUnrealizedJustifiedCheckpoint().getEpoch().longValue());
              writer.writeUInt64(value.getUnrealizedFinalizedCheckpoint().getEpoch().longValue());
              writer.writeFixedBytes(value.getJustifiedCheckpoint().getRoot());
              writer.writeFixedBytes(value.getFinalizedCheckpoint().getRoot());
              writer.writeFixedBytes(value.getUnrealizedJustifiedCheckpoint().getRoot());
              writer.writeFixedBytes(value.getUnrealizedFinalizedCheckpoint().getRoot());
            })
        .toArrayUnsafe();
  }
}
