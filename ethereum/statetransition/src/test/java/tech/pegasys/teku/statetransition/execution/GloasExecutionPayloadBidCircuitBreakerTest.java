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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GloasExecutionPayloadBidCircuitBreakerTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
      mock(ReadOnlyForkChoiceStrategy.class);
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
  private final UInt64 builderIndex = UInt64.valueOf(12);

  @Test
  public void shouldEngageWhenForkChoiceStrategyIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker =
        new GloasExecutionPayloadBidCircuitBreaker(spec, 4, 1, 1, Optional::empty);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldEngageWhenParentRootIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    when(forkChoiceStrategy.contains(parentRoot)).thenReturn(false);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldNotEngageWhenAncestorPayloadsAreAvailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    setAncestor(circuitBreaker, 6, availablePayload(), 1);
    setAncestor(circuitBreaker, 7, availablePayload(), 1);
    setAncestor(circuitBreaker, 8, availablePayload(), 1);
    setAncestor(circuitBreaker, 9, availablePayload(), 1);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isFalse();
  }

  @Test
  public void shouldBanBuilderWhenUnavailablePayloadsExceedAllowedFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 3);
    setAncestor(circuitBreaker, 6, availablePayload(), 12);
    setAncestor(circuitBreaker, 7, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 8, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 9, availablePayload(), 13);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(13), state)).isTrue();
  }

  @Test
  public void shouldNotCountOptimisticPayloadsAsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 0, 0);
    setAncestor(circuitBreaker, 9, optimisticPayload(), builderIndex.intValue());

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(builderIndex, state)).isTrue();
  }

  @Test
  public void shouldKeepBuilderBanWhenLaterPayloadIsAvailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 3);
    setAncestor(circuitBreaker, 7, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 8, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 9, availablePayload(), 12);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isFalse();
  }

  @Test
  public void shouldBanBuilderWhenConsecutiveUnavailablePayloadsExceedAllowedFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 3, 1);
    setAncestor(circuitBreaker, 6, availablePayload(), 12);
    setAncestor(circuitBreaker, 7, availablePayload(), 12);
    setAncestor(circuitBreaker, 8, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 9, unavailablePayload(), 12);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isFalse();
  }

  @Test
  public void shouldBanBuilderWhenConsecutiveFaultStreakSpansMultipleInspections() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(8, 6, 3);
    final BLSPublicKey builderPubkey = dataStructureUtil.randomPublicKey();
    setAncestor(circuitBreaker, 6, availablePayload(), builderIndex.intValue());
    setAncestor(circuitBreaker, 7, unavailablePayload(), builderIndex.intValue());
    setAncestor(circuitBreaker, 8, unavailablePayload(), builderIndex.intValue());
    setAncestor(circuitBreaker, 9, unavailablePayload(), builderIndex.intValue());

    assertThat(
            circuitBreaker.isEngaged(parentRoot, stateAtSlotWithBuilderPubkey(10, builderPubkey)))
        .isFalse();
    assertThat(
            circuitBreaker.isBuilderAllowed(
                builderIndex, stateAtSlotWithBuilderPubkey(10, builderPubkey)))
        .isTrue();

    setAncestor(circuitBreaker, 10, unavailablePayload(), builderIndex.intValue());

    final BeaconState state = stateAtSlotWithBuilderPubkey(11, builderPubkey);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(builderIndex, state)).isFalse();
  }

  @Test
  public void shouldNotCountSkippedConsensusSlotsAsPayloadFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 0, 0);
    final Bytes32 blockAtSlot7 = availablePayload();
    final Bytes32 blockAtSlot9 = availablePayload();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(6)))
        .thenReturn(Optional.empty());
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(7)))
        .thenReturn(Optional.of(blockAtSlot7));
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(8)))
        .thenReturn(Optional.of(blockAtSlot7));
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(9)))
        .thenReturn(Optional.of(blockAtSlot9));
    setBlockSlot(circuitBreaker, blockAtSlot7, 7, 12);
    setBlockSlot(circuitBreaker, blockAtSlot9, 9, 12);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isTrue();
  }

  @Test
  public void shouldEngageWhenAncestorSlotIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    final Bytes32 blockAtSlot9 = availablePayload();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(9)))
        .thenReturn(Optional.of(blockAtSlot9));
    when(forkChoiceStrategy.blockSlot(blockAtSlot9)).thenReturn(Optional.empty());

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldExpireBuilderBanAfterSlotTtl() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 0, 0);
    final BLSPublicKey builderPubkey = dataStructureUtil.randomPublicKey();
    setAncestor(circuitBreaker, 9, unavailablePayload(), 12);

    assertThat(
            circuitBreaker.isEngaged(parentRoot, stateAtSlotWithBuilderPubkey(10, builderPubkey)))
        .isFalse();

    assertThat(
            circuitBreaker.isBuilderAllowed(
                UInt64.valueOf(12), stateAtSlotWithBuilderPubkey(13, builderPubkey)))
        .isFalse();
    assertThat(
            circuitBreaker.isBuilderAllowed(
                UInt64.valueOf(12), stateAtSlotWithBuilderPubkey(14, builderPubkey)))
        .isTrue();
  }

  @Test
  public void shouldNotBanBuilderWhenUnavailablePayloadsAreNotConsecutive() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 3, 1);
    setAncestor(circuitBreaker, 7, unavailablePayload(), 12);
    setAncestor(circuitBreaker, 8, availablePayload(), 12);
    setAncestor(circuitBreaker, 9, unavailablePayload(), 12);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();

    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isTrue();
  }

  @Test
  public void shouldNotCountSameUnavailablePayloadMoreThanOnce() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 3);
    setAncestor(circuitBreaker, 9, unavailablePayload(), 12);

    final BeaconState state = stateAtSlot(10);
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();
    assertThat(circuitBreaker.isEngaged(parentRoot, state)).isFalse();

    assertThat(circuitBreaker.isBuilderAllowed(UInt64.valueOf(12), state)).isTrue();
  }

  @Test
  public void shouldIgnoreBanWhenBuilderIndexIsReusedForDifferentPubkey() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 0, 0);
    final BLSPublicKey originalBuilderPubkey = dataStructureUtil.randomPublicKey();
    final BLSPublicKey replacementBuilderPubkey = dataStructureUtil.randomPublicKey();
    final BeaconState originalBuilderState =
        stateAtSlotWithBuilderPubkey(10, originalBuilderPubkey);
    final BeaconState replacementBuilderState =
        stateAtSlotWithBuilderPubkey(10, replacementBuilderPubkey);
    setAncestor(circuitBreaker, 9, unavailablePayload(), builderIndex.intValue());

    assertThat(circuitBreaker.isEngaged(parentRoot, originalBuilderState)).isFalse();
    assertThat(circuitBreaker.isBuilderAllowed(builderIndex, originalBuilderState)).isFalse();

    assertThat(circuitBreaker.isBuilderAllowed(builderIndex, replacementBuilderState)).isTrue();
  }

  @Test
  public void shouldPruneTrackedBlockPayloadStatusesWhenRecordingImportedBlocks() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);

    for (int slot = 1; slot <= 8; slot++) {
      circuitBreaker.recordBlockBuilder(
          dataStructureUtil.randomBytes32(), UInt64.valueOf(slot), builderIndex);
    }

    assertThat(circuitBreaker.getTrackedBlockPayloadStatusCount()).isEqualTo(5);
  }

  private GloasExecutionPayloadBidCircuitBreaker createCircuitBreaker(
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults) {
    when(forkChoiceStrategy.contains(parentRoot)).thenReturn(true);
    return new GloasExecutionPayloadBidCircuitBreaker(
        spec,
        faultInspectionWindow,
        allowedFaults,
        consecutiveAllowedFaults,
        () -> Optional.of(forkChoiceStrategy));
  }

  private Bytes32 availablePayload() {
    return payload(ProtoNodeValidationStatus.VALID);
  }

  private Bytes32 optimisticPayload() {
    return payload(ProtoNodeValidationStatus.OPTIMISTIC);
  }

  private Bytes32 unavailablePayload() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getBlockData(blockRoot, PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.empty());
    return blockRoot;
  }

  private Bytes32 payload(final ProtoNodeValidationStatus validationStatus) {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getBlockData(blockRoot, PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.of(nodeData(blockRoot, validationStatus)));
    return blockRoot;
  }

  private ProtoNodeData nodeData(
      final Bytes32 blockRoot, final ProtoNodeValidationStatus validationStatus) {
    return new ProtoNodeData(
        UInt64.ZERO,
        blockRoot,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        UInt64.ZERO,
        dataStructureUtil.randomBytes32(),
        UInt64.ZERO,
        validationStatus,
        mock(BlockCheckpoints.class),
        UInt64.ZERO,
        PAYLOAD_STATUS_FULL);
  }

  private void setAncestor(
      final GloasExecutionPayloadBidCircuitBreaker circuitBreaker,
      final int slot,
      final Bytes32 blockRoot,
      final int builderIndex) {
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(slot)))
        .thenReturn(Optional.of(blockRoot));
    setBlockSlot(circuitBreaker, blockRoot, slot, builderIndex);
  }

  private void setBlockSlot(
      final GloasExecutionPayloadBidCircuitBreaker circuitBreaker,
      final Bytes32 blockRoot,
      final int slot,
      final int builderIndex) {
    circuitBreaker.recordBlockBuilder(
        blockRoot, UInt64.valueOf(slot), UInt64.valueOf(builderIndex));
    setBlockSlot(blockRoot, slot);
  }

  private void setBlockSlot(final Bytes32 blockRoot, final int slot) {
    when(forkChoiceStrategy.blockSlot(blockRoot)).thenReturn(Optional.of(UInt64.valueOf(slot)));
  }

  private BeaconState stateAtSlot(final int slot) {
    return dataStructureUtil.stateBuilderGloas(10, 13, 10).slot(UInt64.valueOf(slot)).build();
  }

  private BeaconState stateAtSlotWithBuilderPubkey(
      final int slot, final BLSPublicKey builderPubkey) {
    return stateAtSlot(slot)
        .updated(
            state ->
                MutableBeaconStateGloas.required(state)
                    .getBuilders()
                    .set(
                        builderIndex.intValue(),
                        dataStructureUtil.builderBuilder().publicKey(builderPubkey).build()));
  }
}
