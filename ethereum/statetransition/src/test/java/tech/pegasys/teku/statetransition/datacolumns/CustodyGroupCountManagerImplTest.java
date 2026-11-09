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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImplTest {

  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final CustodyGroupCountChannel custodyGroupCountChannel =
      mock(CustodyGroupCountChannel.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private MiscHelpersFulu miscHelpersFulu;
  private Spec spec;
  private CustodyGroupCountManagerImpl custodyGroupCountManager;

  @Test
  public void defaultFuluConfigWithoutValidatorsSamplingColumnsShouldContainsAllCustodyColumns() {

    setUpManager(4, 8, 8);

    final Set<UInt64> samplingColumnIndices = custodyGroupCountManager.getSamplingColumnIndices();
    // Sampling column groups should always include all custody columns at the minimum.
    assertThat(samplingColumnIndices)
        .containsAll(custodyGroupCountManager.getCustodyColumnIndices());
    assertEquals(8, samplingColumnIndices.size());
  }

  @Test
  public void shouldDefaultCustodyGroupCountIfNotStored() {
    final int custodyCount = 96;
    spec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    when(combinedChainDataClient.getCustodyGroupCount()).thenReturn(Optional.empty());
    custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            spec.getGenesisSpec().miscHelpers().toVersionFulu().orElseThrow(),
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            custodyCount,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(custodyCount);
    verify(combinedChainDataClient, times(1)).getCustodyGroupCount();
    verify(combinedChainDataClient, times(1)).updateCustodyGroupCount(custodyCount);
    verifyNoMoreInteractions(combinedChainDataClient);

    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "custody_groups").getValue())
        .isEqualTo(custodyCount);
  }

  @Test
  public void shouldOnlyUpdateCustodyCountVariableWhenStorageExists() {
    final int custodyCount = 96;
    spec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(custodyCount)));
    custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            spec.getGenesisSpec().miscHelpers().toVersionFulu().orElseThrow(),
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            custodyCount,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(custodyCount);
    verify(combinedChainDataClient, times(1)).getCustodyGroupCount();
    verifyNoMoreInteractions(combinedChainDataClient);

    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "custody_groups").getValue())
        .isEqualTo(custodyCount);
  }

  @Test
  public void shouldNotUpdateCustodyCountVariableWhenUpdateIsLower() {
    final int custodyCount = 96;
    spec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(custodyCount)));
    custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            spec.getGenesisSpec().miscHelpers().toVersionFulu().orElseThrow(),
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            custodyCount,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    reset(combinedChainDataClient);
    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(custodyCount)));
    when(combinedChainDataClient.getBestFinalizedState())
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));

    // update custody to no prepared validators
    assertThat(custodyGroupCountManager.computeAndUpdateCustodyGroupCount(Map.of())).isCompleted();

    // these are needed to compute the group count, and the result will be we don't need to update
    verify(combinedChainDataClient, times(1)).getCustodyGroupCount();
    verify(combinedChainDataClient, times(1)).getBestFinalizedState();
    // so we should expect no update call
    verifyNoMoreInteractions(combinedChainDataClient);
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "custody_groups").getValue())
        .isEqualTo(custodyCount);
    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(custodyCount);
  }

  @Test
  public void onSlot_shouldUpdateCustodyAtGenesis() {
    setUpManager(4, 8, 8);

    custodyGroupCountManager.onSlot(UInt64.ZERO);

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(4);

    // prepare a validator
    when(proposersDataManager.getPreparedProposerInfo())
        .thenReturn(Map.of(UInt64.ZERO, mock(PreparedProposerInfo.class)));

    // make requirements go up
    when(miscHelpersFulu.getValidatorsCustodyRequirement(any(), anySet()))
        .thenReturn(UInt64.valueOf(10));

    custodyGroupCountManager.onSlot(UInt64.ONE);

    // check that we updated to 10, then we can report that we're storing 10.
    verify(combinedChainDataClient).updateCustodyGroupCount(10);
    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(10)));

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(10);

    final Set<UInt64> samplingColumnIndices = custodyGroupCountManager.getSamplingColumnIndices();

    assertThat(samplingColumnIndices)
        .containsAll(custodyGroupCountManager.getCustodyColumnIndices());
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "custody_groups").getValue())
        .isEqualTo(10);
  }

  @Test
  public void onSlot_shouldStopRecomputingWhenSuperNode() {
    // custodyGroupCount = numberOfCustodyGroups = 128, so isSuperNode returns true
    setUpManager(128, 8, 8);
    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(128);

    custodyGroupCountManager.onSlot(UInt64.ZERO);

    // onSlot should exit immediately before calling getPreparedProposerInfo
    verifyNoMoreInteractions(proposersDataManager);
  }

  @Test
  public void onSlot_shouldDeferGenesisInitUntilValidatorsAppear() {
    setUpManager(4, 8, 8);

    // First call at genesis with no validators
    when(proposersDataManager.getPreparedProposerInfo()).thenReturn(Map.of());
    custodyGroupCountManager.onSlot(UInt64.ZERO);

    // No genesis init — getBestFinalizedState not called
    verify(combinedChainDataClient, never()).getBestFinalizedState();

    // Validators appear, still epoch 0 (slot 1, minimal config has 8 slots/epoch)
    when(proposersDataManager.getPreparedProposerInfo())
        .thenReturn(Map.of(UInt64.ZERO, mock(PreparedProposerInfo.class)));
    custodyGroupCountManager.onSlot(UInt64.ONE);

    // Genesis initialization now fires
    verify(combinedChainDataClient, times(1)).getBestFinalizedState();
  }

  @Test
  public void snapshotShouldBeConsistentAfterUpdate() {
    setUpManager(4, 8, 8);
    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(4);

    // Trigger update to custody=10
    when(miscHelpersFulu.getValidatorsCustodyRequirement(any(), anySet()))
        .thenReturn(UInt64.valueOf(10));

    final SafeFuture<Optional<Integer>> result =
        custodyGroupCountManager.computeAndUpdateCustodyGroupCount(
            Map.of(UInt64.ZERO, mock(PreparedProposerInfo.class)));

    assertThat(result).isCompletedWithValue(Optional.of(10));

    // All getters must reflect values computed from the same custody count
    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(10);

    final int expectedSamplingGroupCount = miscHelpersFulu.getSamplingGroupCount(10);
    assertThat(custodyGroupCountManager.getSamplingGroupCount())
        .isEqualTo(expectedSamplingGroupCount);

    final Set<UInt64> custodyColumns = custodyGroupCountManager.getCustodyColumnIndices();
    final Set<UInt64> samplingColumns = custodyGroupCountManager.getSamplingColumnIndices();

    assertThat(custodyColumns).isNotEmpty();
    assertThat(samplingColumns).isNotEmpty();
    // Sampling columns must be a superset of custody columns
    assertThat(samplingColumns).containsAll(custodyColumns);
  }

  @Test
  public void shouldNotFireChannelNotificationWhenCustodyCountUnchanged() {
    setUpManager(4, 8, 8);

    // Initial construction fires the channel notification
    verify(custodyGroupCountChannel, times(1)).onGroupCountUpdate(anyInt(), anyInt());
    reset(custodyGroupCountChannel);

    // Compute again with same result (custody stays at 4)
    when(miscHelpersFulu.getValidatorsCustodyRequirement(any(), anySet()))
        .thenReturn(UInt64.valueOf(4));

    assertThat(
            custodyGroupCountManager.computeAndUpdateCustodyGroupCount(
                Map.of(UInt64.ZERO, mock(PreparedProposerInfo.class))))
        .isCompleted();

    // Channel should NOT be notified since count didn't change
    verifyNoMoreInteractions(custodyGroupCountChannel);
  }

  private void setUpManager(
      final int defaultCustodyRequirement,
      final int defaultSamplesPerSlot,
      final int defaultValidatorCustodyRequirement) {

    spec =
        TestSpecFactory.createMinimalFulu(
            builder ->
                builder.fuluBuilder(
                    fuluBuilder ->
                        fuluBuilder
                            .dataColumnSidecarSubnetCount(128)
                            .cellsPerExtBlob(128)
                            .numberOfColumns(128)
                            .numberOfCustodyGroups(128)
                            .custodyRequirement(defaultCustodyRequirement)
                            .samplesPerSlot(defaultSamplesPerSlot)
                            .validatorCustodyRequirement(defaultValidatorCustodyRequirement)
                            .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                            .minEpochsForDataColumnSidecarsRequests(64)));

    miscHelpersFulu =
        spy(MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers()));

    final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

    custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            miscHelpersFulu,
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            defaultCustodyRequirement,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(defaultCustodyRequirement)));
    when(combinedChainDataClient.getBestFinalizedState())
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));
  }
}
