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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.stategenerator.CheckpointStateGenerator;

class StateAtSlotTaskTest {
  private static final UInt64 EPOCH = UInt64.valueOf(2);

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH);
  private final StateAtSlotTask.AsyncStateProvider stateProvider =
      mock(StateAtSlotTask.AsyncStateProvider.class);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @BeforeEach
  void setUp() {
    chainBuilder.generateGenesis();
  }

  @Test
  void performTask_shouldReturnExistingStateWhenAlreadyAtCorrectSlot() {
    chainBuilder.generateBlocksUpToSlot(slot);
    final SignedBlockAndState base = chainBuilder.getBlockAndStateAtSlot(slot);
    when(stateProvider.getState(base.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(base.getState())));

    final SafeFuture<Optional<BeaconState>> result = createTask(slot, base.getRoot()).performTask();
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(base.getState());
  }

  @Test
  void performTask_shouldThrowInvalidCheckpointExceptionWhenStateIsAheadOfSlot() {
    chainBuilder.generateBlocksUpToSlot(slot.plus(1));
    final SignedBlockAndState base = chainBuilder.getBlockAndStateAtSlot(slot.plus(1));
    when(stateProvider.getState(base.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(base.getState())));

    final SafeFuture<Optional<BeaconState>> result = createTask(slot, base.getRoot()).performTask();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(InvalidCheckpointException.class);
  }

  @Test
  void performTask_shouldProcessSlotsWhenStateIsBeforeSlot() {
    final UInt64 epoch = UInt64.valueOf(2);
    final Checkpoint checkpoint = chainBuilder.getCurrentCheckpointForEpoch(epoch);
    final BeaconState state = chainBuilder.getStateAtSlot(0);

    when(stateProvider.getState(checkpoint.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final StateAtSlotTask task =
        createTask(checkpoint.getEpochStartSlot(spec), checkpoint.getRoot());
    final SafeFuture<Optional<BeaconState>> result = task.performTask();
    final BeaconState expectedState =
        CheckpointStateGenerator.regenerateCheckpointState(spec, checkpoint, state);
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(expectedState);
  }

  @Test
  void performTask_shouldCreateStateForSlotNotAtEpochBoundary() throws Exception {
    final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(0);

    final UInt64 slot = UInt64.valueOf(5);
    when(stateProvider.getState(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getState())));

    final StateAtSlotTask task = createTask(slot, blockAndState.getRoot());
    final SafeFuture<Optional<BeaconState>> result = task.performTask();
    final BeaconState expectedState = spec.processSlots(blockAndState.getState(), slot);
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(expectedState);
  }

  @Test
  void streamIntermediateSteps_shouldIncludeCheckpointsFromPreviousSlots() {
    final Bytes32 root = Bytes32.fromHexStringLenient("0x1234");
    final List<SlotAndBlockRoot> expected = new ArrayList<>();
    final UInt64 slot = UInt64.valueOf(670);
    for (int i = slot.intValue() - 1; i >= 30; i--) {
      expected.add(new SlotAndBlockRoot(UInt64.valueOf(i), root));
    }
    assertThat(createTask(slot, root).streamIntermediateSteps())
        .containsExactlyElementsOf(expected);
  }

  @Test
  void streamIntermediateSteps_shouldStopAtSlotZero() {
    final Bytes32 root = Bytes32.fromHexStringLenient("0x1234");
    final List<SlotAndBlockRoot> expected = new ArrayList<>();
    final UInt64 slot = UInt64.valueOf(5);
    for (int i = slot.intValue() - 1; i >= 0; i--) {
      expected.add(new SlotAndBlockRoot(UInt64.valueOf(i), root));
    }
    assertThat(createTask(slot, root).streamIntermediateSteps())
        .containsExactlyElementsOf(expected);
  }

  @Test
  void rebase_shouldUseSuppliedStateAsNewStartingPoint() {
    final SignedBlockAndState newBase = chainBuilder.generateBlockAtSlot(slot.minus(1));
    final Checkpoint realCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(slot);
    final BeaconState expectedState =
        CheckpointStateGenerator.regenerateCheckpointState(
            spec, realCheckpoint, newBase.getState());
    final StateAtSlotTask task =
        createTask(realCheckpoint.getEpochStartSlot(spec), Bytes32.fromHexStringLenient("0x12"));

    final CachingTaskQueue.CacheableTask<SlotAndBlockRoot, BeaconState> rebasedTask =
        task.rebase(newBase.getState());
    assertThatSafeFuture(rebasedTask.performTask())
        .isCompletedWithOptionalContaining(expectedState);
    verifyNoInteractions(stateProvider);
  }

  private StateAtSlotTask createTask(final UInt64 slot, final Bytes32 root) {
    return new StateAtSlotTask(spec, new SlotAndBlockRoot(slot, root), stateProvider);
  }
}
