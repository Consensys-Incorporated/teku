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

package tech.pegasys.teku.storage.protoarray;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

public class ProtoArrayTestUtil {

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(final int i) {
    return MathHelpers.uintToBytes32(Integer.toUnsignedLong(i + 1));
  }

  public static ForkChoiceStrategy createProtoArrayForkChoiceStrategy(
      final Spec spec,
      final Bytes32 finalizedBlockRoot,
      final UInt64 finalizedBlockSlot,
      final UInt64 finalizedCheckpointEpoch,
      final UInt64 justifiedCheckpointEpoch) {

    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(spec)
            .currentEpoch(ZERO)
            .justifiedCheckpoint(new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO))
            .finalizedCheckpoint(new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO))
            .build();

    ForkChoiceStrategy forkChoice = ForkChoiceStrategy.initialize(spec, protoArray);

    forkChoice.processBlock(
        finalizedBlockSlot,
        finalizedBlockRoot,
        Bytes32.ZERO,
        Bytes32.ZERO,
        new BlockCheckpoints(
            new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO)),
        Optional.empty(),
        Optional.empty());

    return forkChoice;
  }

  public static TestStoreImpl createStoreToManipulateVotes() {
    return new TestStoreFactory().createGenesisStore();
  }
}
