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

package tech.pegasys.teku.statetransition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class EpochCachePrimerTest {

  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private final Spec realSpec = TestSpecFactory.createMinimalPhase0();
  private final Spec mockSpec = mock(Spec.class);
  private final BeaconStateUtil beaconStateUtil = mock(BeaconStateUtil.class);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final EpochCachePrimer primer =
      new EpochCachePrimer(mockSpec, recentChainData, asyncRunner);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChainUntil(5);
    storageSystem.chainUpdater().updateBestBlock(head);

    // Delegate to spec
    when(mockSpec.getSlotsPerEpoch(any())).thenReturn(realSpec.getSlotsPerEpoch(UInt64.ZERO));
    when(mockSpec.computeStartSlotAtEpoch(any()))
        .thenAnswer(invocation -> realSpec.computeStartSlotAtEpoch(invocation.getArgument(0)));
    when(mockSpec.getMaxLookaheadEpoch(any()))
        .thenAnswer(invocation -> realSpec.getMaxLookaheadEpoch(invocation.getArgument(0)));
    when(mockSpec.computeEpochAtSlot(any()))
        .thenAnswer(invocation -> realSpec.computeEpochAtSlot(invocation.getArgument(0)));
    when(mockSpec.getCurrentEpoch(any(BeaconState.class)))
        .thenAnswer(invocation -> realSpec.getCurrentEpoch(invocation.<BeaconState>getArgument(0)));
    when(mockSpec.getSpecConfig(any()))
        .thenAnswer(invocation -> realSpec.getSpecConfig(invocation.getArgument(0)));

    when(mockSpec.getBeaconStateUtil(any())).thenReturn(beaconStateUtil);

    when(mockSpec.getCommitteeCountPerSlot(any(), any()))
        .thenAnswer(
            invocation ->
                realSpec.getCommitteeCountPerSlot(
                    invocation.getArgument(0), invocation.getArgument(1)));
  }

  @Test
  void shouldNotPrecomputeEpochsBeforeHeadBlock() {
    primer.primeCacheForEpoch(UInt64.ZERO);
    asyncRunner.executeQueuedActions();

    verify(mockSpec, never()).getBeaconProposerIndex(any(), any());
  }

  @Test
  void shouldNotPrecomputeMoreThanOneEpochAhead() {
    primer.primeCacheForEpoch(UInt64.valueOf(2));
    asyncRunner.executeQueuedActions();

    verify(mockSpec, never()).getBeaconProposerIndex(any(), any());
  }

  @Test
  void shouldPrecomputeProposersForEpoch() {
    final UInt64 epoch = UInt64.ONE;

    primer.primeCacheForEpoch(epoch);
    asyncRunner.executeQueuedActions();

    final BeaconState state = getStateForEpoch(epoch);
    forEachSlotInEpoch(epoch, slot -> verify(mockSpec).getBeaconProposerIndex(state, slot));
  }

  @Test
  void shouldPrimeAsynchronously() {
    final UInt64 epoch = UInt64.ONE;

    primer.primeCacheForEpoch(epoch);

    verify(mockSpec, never()).getBeaconProposerIndex(any(), any());

    asyncRunner.executeQueuedActions();
    final BeaconState state = getStateForEpoch(epoch);
    forEachSlotInEpoch(epoch, slot -> verify(mockSpec).getBeaconProposerIndex(state, slot));
  }

  @Test
  void shouldPrecomputeAttestersTotalEffectiveBalance() {
    final UInt64 epoch = UInt64.ONE;

    primer.primeCacheForEpoch(epoch);
    asyncRunner.executeQueuedActions();

    final BeaconState state = getStateForEpoch(epoch);
    forEachSlotInEpoch(
        epoch, slot -> verify(beaconStateUtil).getAttestersTotalEffectiveBalance(state, slot));
  }

  @Test
  void shouldComputeCommitteesForMaxLookAheadEpoch() {
    final UInt64 epoch = UInt64.ONE;

    primer.primeCacheForEpoch(epoch);
    asyncRunner.executeQueuedActions();

    final BeaconState state = getStateForEpoch(epoch);
    final UInt64 lookaheadEpoch = epoch.plus(1);
    forEachSlotInEpoch(
        lookaheadEpoch, slot -> verify(mockSpec).getBeaconCommitteesSize(state, slot));

    final UInt64 firstSlotAfterLookAheadPeriod =
        realSpec.computeStartSlotAtEpoch(lookaheadEpoch.plus(1));
    // Should not precalculate beyond the end of the look ahead period
    verify(mockSpec, never())
        .getBeaconCommitteesSize(
            any(),
            argThat(argument -> argument.isGreaterThanOrEqualTo(firstSlotAfterLookAheadPeriod)));
  }

  @Test
  void shouldPrimeJustifiedCheckpoint() {
    // Make sure we have a justified checkpoint to prime.
    final SignedBlockAndState newHead = storageSystem.chainUpdater().finalizeCurrentChain();
    final UInt64 epoch = realSpec.getCurrentEpoch(newHead.getState()).plus(1);

    primer.primeCacheForEpoch(epoch);
    asyncRunner.executeQueuedActions();

    final BeaconState state = getStateForEpoch(epoch);
    final BeaconState justifiedState =
        safeJoin(recentChainData.retrieveCheckpointState(state.getCurrentJustifiedCheckpoint()))
            .orElseThrow();
    verify(beaconStateUtil).getEffectiveActiveUnslashedBalances(justifiedState);
  }

  private void forEachSlotInEpoch(final UInt64 epoch, final Consumer<UInt64> action) {
    UInt64.range(
            realSpec.computeStartSlotAtEpoch(epoch),
            realSpec.computeStartSlotAtEpoch(epoch.plus(1)))
        .forEach(action);
  }

  private BeaconState getStateForEpoch(final UInt64 epoch) {
    final Bytes32 headBlock = recentChainData.getBestBlockRoot().orElseThrow();
    final SafeFuture<Optional<BeaconState>> stateFuture =
        recentChainData.retrieveBlockState(
            new SlotAndBlockRoot(realSpec.computeStartSlotAtEpoch(epoch), headBlock));
    assertThatSafeFuture(stateFuture).isCompletedWithNonEmptyOptional();
    return stateFuture.getNow(null).orElseThrow();
  }
}
