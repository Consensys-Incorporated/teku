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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState.Mutator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;

class BeaconChainMetricsTest {

  private static final UInt64 NODE_SLOT_VALUE = UInt64.valueOf(100L);
  private final Spec spec = TestSpecFactory.createMainnetPhase0();
  private final int slotsPerHistoricalRoot =
      spec.getGenesisSpecConfig().getSlotsPerHistoricalRoot();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Bytes32 root =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFFFF");
  private final Bytes32 root2 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFF7F");
  private final Bytes32 root3 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24e0000000000000080");
  private SignedBlockAndState chainHead;

  private final NodeSlot nodeSlot = new NodeSlot(NODE_SLOT_VALUE);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final RecentChainData preGenesisChainData = MemoryOnlyRecentChainData.create();
  private final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final Checkpoint finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
  private final Checkpoint currentJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
  private final Checkpoint previousJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final BeaconChainMetrics beaconChainMetrics =
      new BeaconChainMetrics(
          spec, recentChainData, nodeSlot, metricsSystem, eth2P2PNetwork, eth1DataCache);

  @BeforeEach
  void setUp() {
    // Update state
    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(1000, dataStructureUtil.randomBytes32()));

    final BeaconState state =
        dataStructureUtil
            .stateBuilderPhase0()
            .slot(NODE_SLOT_VALUE)
            .build()
            .updated(
                s -> {
                  s.setFinalizedCheckpoint(finalizedCheckpoint);
                  s.setCurrentJustifiedCheckpoint(currentJustifiedCheckpoint);
                  s.setPreviousJustifiedCheckpoint(previousJustifiedCheckpoint);
                  s.getBlockRoots().setAllElements(blockRootsList);
                });
    chainHead = dataStructureUtil.randomSignedBlockAndState(state);

    when(recentChainData.getChainHead()).thenAnswer(__ -> Optional.of(ChainHead.create(chainHead)));
  }

  private <E extends RuntimeException> void updateState(
      final Mutator<MutableBeaconStatePhase0, E, E, E> mutator) {
    final BeaconState updatedState =
        chainHead.getState().toVersionPhase0().orElseThrow().updatedPhase0(mutator);
    chainHead = dataStructureUtil.randomSignedBlockAndState(updatedState);
  }

  private void setBlockRoots(final List<Bytes32> newBlockRoots) {
    updateState(s -> s.getBlockRoots().setAllElements(newBlockRoots));
  }

  @Test
  void getLongFromRoot_shouldParseNegativeOne() {
    assertThat(-1L).isEqualTo(BeaconChainMetrics.getLongFromRoot(root));
  }

  @Test
  void getLongFromRoot_shouldParseMaxLong() {
    assertThat(Long.MAX_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root2));
  }

  @Test
  void getLongFromRoot_shouldParseMinLong() {
    assertThat(Long.MIN_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root3));
  }

  @Test
  void getCurrentSlotValue_shouldReturnCurrentSlot() {
    assertThat(metricsSystem.getGauge(BEACON, "slot").getValue())
        .isEqualTo(NODE_SLOT_VALUE.longValue());
  }

  @Test
  void getHeadSlotValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getHeadSlot()).thenReturn(ONE);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isEqualTo(1L);
  }

  @Test
  void getHeadSlotValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue())
        .isEqualTo(chainHead.getState().getFinalizedCheckpoint().getEpoch().longValue());
  }

  @Test
  void getPeerCount_shouldSupplyValueIncludingConnectionDirectionLabel() {
    final List<Eth2Peer> peers =
        IntStream.range(0, 9)
            .mapToObj(
                i -> {
                  final Eth2Peer peer = mock(Eth2Peer.class);
                  if (i % 2 == 0) {
                    // outbound
                    when(peer.connectionInitiatedLocally()).thenReturn(true);
                    when(peer.connectionInitiatedRemotely()).thenReturn(false);
                  } else {
                    // inbound
                    when(peer.connectionInitiatedLocally()).thenReturn(false);
                    when(peer.connectionInitiatedRemotely()).thenReturn(true);
                  }
                  return peer;
                })
            .toList();

    when(eth2P2PNetwork.streamPeers()).thenAnswer(__ -> peers.stream());

    assertThat(metricsSystem.getLabelledGauge(BEACON, "peer_count").getValue("inbound"))
        .hasValue(4);
    assertThat(metricsSystem.getLabelledGauge(BEACON, "peer_count").getValue("outbound"))
        .hasValue(5);
  }

  @Test
  void getHeadRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isZero();
  }

  @Test
  void getHeadRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getFinalizedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue()).isZero();
  }

  @Test
  void getFinalizedRootValue_shouldReturnValueWhenStoreIsPresent() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue())
        .isEqualTo(
            BeaconChainMetrics.getLongFromRoot(
                chainHead.getState().getFinalizedCheckpoint().getRoot()));
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue())
        .isEqualTo(chainHead.getState().getPreviousJustifiedCheckpoint().getEpoch().longValue());
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue())
        .isEqualTo(
            BeaconChainMetrics.getLongFromRoot(
                chainHead.getState().getPreviousJustifiedCheckpoint().getRoot()));
  }

  @Test
  void getJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue()).isZero();
  }

  @Test
  void getJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue())
        .isEqualTo(
            BeaconChainMetrics.getLongFromRoot(
                chainHead.getState().getCurrentJustifiedCheckpoint().getRoot()));
  }

  @Test
  void getJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue()).isZero();
  }

  @Test
  void getJustifiedEpochValue_shouldReturnValueWhenStoreIsPresent() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue())
        .isEqualTo(chainHead.getState().getCurrentJustifiedCheckpoint().getEpoch().longValue());
  }

  @Test
  void getCurrentEpochValue_shouldReturnValueWhenNodeSlotIsSet() {
    final UInt64 epochAtSlot = spec.computeEpochAtSlot(nodeSlot.getValue());
    assertThat(metricsSystem.getGauge(BEACON, "epoch").getValue())
        .isEqualTo(epochAtSlot.longValue());
  }

  @Test
  void onSlot_shouldUpdateEth1DataMetrics() {
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    verify(eth1DataCache).updateMetrics(chainHead.getState());
  }

  @Test
  void activeValidators_retrievesCorrectValue() {
    final UInt64 slotNumber = spec.computeStartSlotAtEpoch(UInt64.valueOf(13));
    final List<Validator> validators =
        List.of(
            validator(13, 15, false),
            validator(14, 15, false),
            validator(10, 12, false),
            validator(10, 15, true));

    withSlotCurrentEpochAttestationsAndValidators(slotNumber, Collections.emptyList(), validators);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_active_validators").getValue()).isEqualTo(2);
    assertThat(metricsSystem.getGauge(BEACON, "previous_active_validators").getValue())
        .isEqualTo(1);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInDifferentSlotAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(14, 1, bitlist))
            .collect(toList());
    withCurrentEpochAttestations(attestations);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInDifferentCommitteeAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(13, 2, bitlist))
            .collect(toList());
    withCurrentEpochAttestations(attestations);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInSameSlotAsOneValidator() {
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(1, 2, 3, 4);
    withCurrentEpochAttestations(createAttestations(13, 1, bitlist1, bitlist2).collect(toList()));

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(6);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInDifferentSlotAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(14, 1, bitlist))
            .collect(toList());
    withPreviousEpochAttestations(100, attestations);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInDifferentCommitteeAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(13, 2, bitlist))
            .collect(toList());
    withPreviousEpochAttestations(100, attestations);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInSameSlotAsOneValidator() {
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(1, 2, 3, 4);
    withPreviousEpochAttestations(
        100, createAttestations(13, 1, bitlist1, bitlist2).collect(toList()));

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(6);
  }

  @Test
  void currentCorrectValidators_onlyCountValidatorsWithCorrectTarget() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    blockRootsList.set(
        target.getEpochStartSlot(spec).mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(13, 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    13,
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    withCurrentEpochAttestations(allAttestations, UInt64.valueOf(15));

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "current_correct_validators").getValue())
        .isEqualTo(4);
  }

  @Test
  void currentCorrectValidators_withStateAtFirstSlotOfEpoch() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(slot), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    blockRootsList.set(slot.mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(slot.intValue(), 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    slot.intValue(),
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(slot), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    withCurrentEpochAttestations(allAttestations, slot);

    // Make sure we don't try to get the block root for the state's own slot from block roots array
    // Otherwise this will fail.
    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
  }

  @Test
  void previousCorrectValidators_onlyCountValidatorsWithCorrectTarget() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    final int blockRootIndex =
        target.getEpochStartSlot(spec).mod(slotsPerHistoricalRoot).intValue();
    blockRootsList.set(blockRootIndex, blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(13, 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    15,
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    final int slotInNextEpoch = spec.computeStartSlotAtEpoch(UInt64.ONE).plus(13).intValue();
    withPreviousEpochAttestations(slotInNextEpoch, allAttestations);

    assertThat(beaconChainMetrics.updateMetrics()).isCompleted();
    assertThat(metricsSystem.getGauge(BEACON, "previous_correct_validators").getValue())
        .isEqualTo(4);
  }

  private void withCurrentEpochAttestations(final List<PendingAttestation> attestations) {
    withSlotCurrentEpochAttestationsAndValidators(
        UInt64.valueOf(100), attestations, Collections.emptyList());
  }

  private void withCurrentEpochAttestations(
      final List<PendingAttestation> attestations, final UInt64 slot) {
    withSlotCurrentEpochAttestationsAndValidators(slot, attestations, Collections.emptyList());
  }

  private void withPreviousEpochAttestations(
      final int slotAsInt, final List<PendingAttestation> attestations) {
    updateState(
        s -> {
          s.getPreviousEpochAttestations().setAll(attestations);

          s.getCurrentEpochAttestations().clear();
          s.setSlot(UInt64.valueOf(slotAsInt));
        });
  }

  private void withSlotCurrentEpochAttestationsAndValidators(
      final UInt64 slot,
      final List<PendingAttestation> attestations,
      final List<Validator> validatorsList) {
    updateState(
        s -> {
          s.getCurrentEpochAttestations().clear();
          if (attestations.size() > 0) {
            s.getCurrentEpochAttestations().setAll(attestations);
          }

          s.getPreviousEpochAttestations().clear();
          s.setSlot(slot);

          s.getValidators().clear();
          if (validatorsList.size() > 0) {
            s.getValidators().setAll(validatorsList);
          }
        });
  }

  private Stream<PendingAttestation> createAttestations(
      final int slot, final int index, final SszBitlist... bitlists) {
    return Stream.of(bitlists)
        .map(
            bitlist1 ->
                BeaconStateSchemaPhase0.required(
                        spec.getGenesisSchemaDefinitions().getBeaconStateSchema())
                    .getPendingAttestationSchema()
                    .create(
                        bitlist1,
                        new AttestationData(
                            UInt64.valueOf(slot),
                            UInt64.valueOf(index),
                            dataStructureUtil.randomBytes32(),
                            dataStructureUtil.randomCheckpoint(),
                            dataStructureUtil.randomCheckpoint()),
                        dataStructureUtil.randomUInt64(),
                        dataStructureUtil.randomUInt64()));
  }

  private Stream<PendingAttestation> createAttestationsWithTargetCheckpoint(
      final int slot, final int index, final Checkpoint target, final SszBitlist... bitlists) {
    return Stream.of(bitlists)
        .map(
            bitlist1 ->
                BeaconStateSchemaPhase0.required(
                        spec.getGenesisSchemaDefinitions().getBeaconStateSchema())
                    .getPendingAttestationSchema()
                    .create(
                        bitlist1,
                        new AttestationData(
                            UInt64.valueOf(slot),
                            UInt64.valueOf(index),
                            dataStructureUtil.randomBytes32(),
                            dataStructureUtil.randomCheckpoint(),
                            target),
                        dataStructureUtil.randomUInt64(),
                        dataStructureUtil.randomUInt64()));
  }

  private SszBitlist bitlistOf(final int... indices) {
    return spec.getGenesisSchemaDefinitions()
        .getAttestationSchema()
        .getAggregationBitsSchema()
        .ofBits(10, indices);
  }

  private Validator validator(
      final long activationEpoch, final long exitEpoch, final boolean slashed) {
    return new Validator(
        dataStructureUtil.randomPublicKeyBytes(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        slashed,
        UInt64.valueOf(activationEpoch),
        UInt64.valueOf(activationEpoch),
        UInt64.valueOf(exitEpoch),
        UInt64.valueOf(exitEpoch));
  }
}
