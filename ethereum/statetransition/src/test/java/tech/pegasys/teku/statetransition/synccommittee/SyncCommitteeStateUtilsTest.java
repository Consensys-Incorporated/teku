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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncCommitteeStateUtilsTest {

  private static final int EPOCHS_PER_SYNC_COMMITTEE_PERIOD = 10;
  private static final UInt64 ALTAIR_FORK_EPOCH = UInt64.ONE;
  private final Spec spec =
      TestSpecFactory.createAltair(
          SpecConfigLoader.loadConfig(
              "minimal",
              builder ->
                  builder
                      .altairForkEpoch(ALTAIR_FORK_EPOCH)
                      .altairBuilder(
                          altairBuilder ->
                              altairBuilder.epochsPerSyncCommitteePeriod(
                                  EPOCHS_PER_SYNC_COMMITTEE_PERIOD))));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.atEpoch(ALTAIR_FORK_EPOCH).getSyncCommitteeUtil().orElseThrow();

  private final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final SyncCommitteeStateUtils stateUtils =
      new SyncCommitteeStateUtils(spec, recentChainData);

  @Test
  void shouldReturnEmptyWhenChainHeadIsNotAvailable() {
    when(recentChainData.getChainHead()).thenReturn(Optional.empty());
    assertThatSafeFuture(stateUtils.getStateForSyncCommittee(UInt64.ONE))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void shouldUseChainHeadStateWhenSlotEqualToRequestedSlot() {
    final UInt64 slot = UInt64.valueOf(500);
    // State from same slot as signature so definitely within the committee period.
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().slot(slot).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldUseChainHeadStateWhenBlockSlotWithinSameCommitteePeriod() {
    final UInt64 slot = UInt64.valueOf(500);
    // Few skip slots but still in the right region
    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(slot.minusMinZero(3)).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldUseChainHeadStateWhenInMinimumRequiredEpoch() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 stateSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch));
    // Few skip slots but still in the right region
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().slot(stateSlot).build();
    assertRetrievedStateIsSuitable(slot, state);
  }

  @Test
  void shouldProcessSlotsIfStateIsWithinAnEpochOfForkSlot() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(ALTAIR_FORK_EPOCH);
    // Block is from before the Altair for sow we will need to process slots up to the fork slot
    final UInt64 generatedStateSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(ALTAIR_FORK_EPOCH));

    final BeaconStateAltair headState =
        dataStructureUtil
            .stateBuilderAltair()
            .slot(UInt64.ZERO)
            .latestBlockHeader(
                new BeaconBlockHeader(
                    UInt64.ZERO,
                    dataStructureUtil.randomUInt64(),
                    dataStructureUtil.randomBytes32(),
                    Bytes32.ZERO,
                    dataStructureUtil.randomBytes32()))
            .build();
    final ChainHead chainHead = ChainHead.create(StateAndBlockSummary.create(headState));
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));

    final BeaconStateAltair generatedState =
        dataStructureUtil.stateBuilderAltair().slot(generatedStateSlot).build();
    when(recentChainData.retrieveBlockState(
            new SlotAndBlockRoot(generatedStateSlot, chainHead.getRoot())))
        .thenReturn(SafeFuture.completedFuture(Optional.of(generatedState)));

    final SafeFuture<Optional<BeaconStateAltair>> result =
        stateUtils.getStateForSyncCommittee(slot);

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(generatedState);
  }

  @Test
  void shouldReturnEmptyWhenChainHeadStateIsTooOld() {
    final UInt64 slot = UInt64.valueOf(500);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 blockSlot =
        spec.computeStartSlotAtEpoch(
                syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch).minus(1))
            .minus(1);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(blockSlot));

    assertThatSafeFuture(stateUtils.getStateForSyncCommittee(slot)).isCompletedWithEmptyOptional();
    verify(recentChainData, never()).retrieveBlockState(any(SlotAndBlockRoot.class));
  }

  private void assertRetrievedStateIsSuitable(final UInt64 slot, final BeaconState state) {
    final ChainHead chainHead = ChainHead.create(StateAndBlockSummary.create(state));
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));

    final SafeFuture<Optional<BeaconStateAltair>> result =
        stateUtils.getStateForSyncCommittee(slot);

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(safeJoin(chainHead.getState()));
  }
}
