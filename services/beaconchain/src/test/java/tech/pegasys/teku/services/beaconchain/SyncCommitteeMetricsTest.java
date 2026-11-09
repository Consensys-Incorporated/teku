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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncCommitteeMetricsTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final int slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

  private final SyncCommitteeMetrics syncCommitteeMetrics =
      new SyncCommitteeMetrics(spec, recentChainData, metricsSystem);
  private BeaconStateAltair state;

  @BeforeEach
  void setUp() {
    final List<Bytes32> roots = new ArrayList<>();
    for (int i = 0; i < spec.getGenesisSpecConfig().getSlotsPerHistoricalRoot(); i++) {
      roots.add(dataStructureUtil.randomBytes32());
    }
    state =
        dataStructureUtil
            .stateBuilderAltair()
            .blockRoots(roots)
            .slot(spec.computeStartSlotAtEpoch(UInt64.valueOf(1)))
            .build();
  }

  @Test
  void shouldNotUpdateWhenChainHeadIsNotInAltair() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconState chainHead = dataStructureUtil.randomBeaconState(50);
    syncCommitteeMetrics.updateSlotBasedMetrics(chainHead.getSlot(), chainHead);

    verifyNoInteractions(recentChainData);
  }

  @Test
  void shouldUpdatePreviousLiveSyncCommittee() {
    fillBlocks();

    syncCommitteeMetrics.updateSlotBasedMetrics(state.getSlot(), state);

    final int expected = 3 * slotsPerEpoch;
    assertPreviousLiveSyncCommitteeMetric(expected);
  }

  @Test
  void shouldNotDoubleCountBlocksWhenSlotIsSkipped() {
    fillBlocks();

    // Override slot 5 to be empty (so the block is actually from the previous slot)
    final Bytes32 slot5Root = spec.getBlockRootAtSlot(state, UInt64.valueOf(5));
    when(recentChainData.getSlotForBlockRoot(slot5Root)).thenReturn(Optional.of(UInt64.valueOf(4)));

    syncCommitteeMetrics.updateSlotBasedMetrics(state.getSlot(), state);

    verify(recentChainData, never()).retrieveBlockByRoot(slot5Root);
    assertPreviousLiveSyncCommitteeMetric(3 * (slotsPerEpoch - 1));
  }

  @Test
  void shouldSetPreviousLiveToZeroWhenChainHeadIsBeforePreviousEpoch() {
    fillBlocks();
    syncCommitteeMetrics.updateSlotBasedMetrics(state.getSlot(), state);
    assertPreviousLiveSyncCommitteeMetric(3 * slotsPerEpoch);

    // Time progresses but with no update to chain head
    syncCommitteeMetrics.updateSlotBasedMetrics(state.getSlot().plus(slotsPerEpoch * 3L), state);
    assertPreviousLiveSyncCommitteeMetric(0);
  }

  private void fillBlocks() {
    for (int i = 0; i < slotsPerEpoch; i++) {
      final Bytes32 blockRoot = spec.getBlockRootAtSlot(state, UInt64.valueOf(i));
      final BeaconBlock block =
          safeJoin(
              dataStructureUtil
                  .blockBuilder(i)
                  .syncAggregate(dataStructureUtil.randomSyncAggregate(1, 2, 3))
                  .build());
      when(recentChainData.getSlotForBlockRoot(blockRoot))
          .thenReturn(Optional.of(UInt64.valueOf(i)));
      when(recentChainData.retrieveBlockByRoot(blockRoot))
          .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    }
  }

  private void assertPreviousLiveSyncCommitteeMetric(final int expected) {
    assertThat(
            metricsSystem
                .getGauge(TekuMetricCategory.BEACON, "previous_live_sync_committee")
                .getValue())
        .isEqualTo(expected);
  }
}
