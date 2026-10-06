/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil.BlockRewardData;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair.AttestationProcessingResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockRewardCalculatorUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil data = new DataStructureUtil(spec);

  private final BlockRewardCalculatorUtil calculator = new BlockRewardCalculatorUtil(spec);

  private final BlockProcessorAltair blockProcessorAltair = mock(BlockProcessorAltair.class);

  private static final long SINGLE_SLASHING_REWARD = 62500000L;

  @Test
  void getBlockRewardData_shouldOutputAttesterSlashings() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .attesterSlashings(data.randomAttesterSlashings(1, 100))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.attesterSlashings()).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void getBlockRewardData_shouldOutputProposerSlashings() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .proposerSlashings(data.randomProposerSlashings(1, 100))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.proposerSlashings()).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void getBlockRewardData_shouldOutputSyncAggregate() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .syncAggregate(data.randomSyncAggregate(1, 2, 3, 4))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.syncAggregate()).isEqualTo(140L);
  }

  @Test
  void getBlockRewardData_shouldOutputAttestations() {
    // these values are invalid, but basically count each attestation as 1, so the attestation
    // reward equals the number of attestations
    when(blockProcessorAltair.createIndexedAttestationProvider(any(), any()))
        .thenReturn(mock(AbstractBlockProcessor.IndexedAttestationProvider.class));
    when(blockProcessorAltair.processAttestation(any(), any(), any()))
        .thenReturn(new AttestationProcessingResult(Optional.of(UInt64.ONE), 0, UInt64.ZERO));
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .attestations(data.randomAttestations(10, preState.getSlot().decrement()))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.attestations()).isEqualTo(10L);
    verify(blockProcessorAltair, times(10)).processAttestation(any(), any(), any());
  }

  @Test
  void calculateBlockRewards_shouldApplyFullParentAvailabilityForGloasAttestations() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final BlockRewardCalculatorUtil gloasCalculator = new BlockRewardCalculatorUtil(gloasSpec);
    final BlockProcessorAltair gloasBlockProcessor = mock(BlockProcessorAltair.class);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(gloasSpec.getGenesisSchemaDefinitions());
    final BeaconBlockBodySchema<?> bodySchema = schemaDefinitions.getBeaconBlockBodySchema();
    final UInt64 parentSlot = UInt64.valueOf(8);
    final UInt64 blockSlot = parentSlot.plus(2);
    final int parentSlotIndex =
        parentSlot.mod(gloasSpec.getSlotsPerHistoricalRoot(parentSlot)).intValue();
    // Bid slot is deliberately one behind the header slot — isBidBuildingOnFullParent only checks
    // blockHash, so this verifies availability is indexed by the header slot, not the bid slot.
    final UInt64 staleBidSlot = parentSlot.minus(1);
    final ExecutionPayloadBid parentBid =
        gloasData.randomExecutionPayloadBid(staleBidSlot, UInt64.ZERO);
    final BeaconStateGloas preState =
        BeaconStateGloas.required(
            gloasData
                .randomBeaconState(blockSlot)
                .updated(
                    mutableState -> {
                      final MutableBeaconStateGloas stateGloas =
                          MutableBeaconStateGloas.required(mutableState);
                      stateGloas.setLatestBlockHeader(
                          gloasData.randomBeaconBlockHeader(parentSlot, UInt64.ZERO));
                      stateGloas.setLatestExecutionPayloadBid(parentBid);
                      stateGloas.setExecutionPayloadAvailability(
                          schemaDefinitions.getExecutionPayloadAvailabilitySchema().getDefault());
                    }));
    final SignedExecutionPayloadBid childBid =
        gloasData.randomSignedExecutionPayloadBid(
            gloasData.randomExecutionPayloadBid(
                parentBid.getBlockHash(), blockSlot, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO));
    final BeaconBlockBody blockBody =
        gloasData.randomBeaconBlockBody(
            blockSlot,
            builder ->
                builder
                    .signedExecutionPayloadBid(childBid)
                    .attestations(bodySchema.getAttestationsSchema().getDefault())
                    .proposerSlashings(bodySchema.getProposerSlashingsSchema().getDefault())
                    .attesterSlashings(bodySchema.getAttesterSlashingsSchema().getDefault()));
    final BeaconBlock block = gloasData.randomBeaconBlock(blockSlot, blockBody);
    when(gloasBlockProcessor.createIndexedAttestationProvider(any(), any()))
        .thenReturn(mock(AbstractBlockProcessor.IndexedAttestationProvider.class));

    gloasCalculator.calculateBlockRewards(block, gloasBlockProcessor, preState);

    final ArgumentCaptor<BeaconState> stateCaptor = ArgumentCaptor.forClass(BeaconState.class);
    verify(gloasBlockProcessor).createIndexedAttestationProvider(stateCaptor.capture(), any());
    final BeaconStateGloas attestationRewardState =
        BeaconStateGloas.required(stateCaptor.getValue());
    assertThat(attestationRewardState.getExecutionPayloadAvailability().getBit(parentSlotIndex))
        .isTrue();
    assertThat(preState.getExecutionPayloadAvailability().getBit(parentSlotIndex)).isFalse();
  }

  @Test
  void getBlockRewardData_shouldOutputRewardData() {
    when(blockProcessorAltair.createIndexedAttestationProvider(any(), any()))
        .thenReturn(mock(AbstractBlockProcessor.IndexedAttestationProvider.class));
    when(blockProcessorAltair.processAttestation(any(), any(), any()))
        .thenReturn(new AttestationProcessingResult(Optional.of(UInt64.ONE), 0, UInt64.ZERO));
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .executionPayload(data.randomExecutionPayload())
            .proposerSlashings(data.randomProposerSlashings(3, 100))
            .attesterSlashings(data.randomAttesterSlashings(2, 100))
            .attestations(data.randomAttestations(10, preState.getSlot().decrement()))
            .syncAggregate(data.randomSyncAggregate(1, 2, 3, 4))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward)
        .isEqualTo(
            new BlockRewardData(
                block.getProposerIndex(),
                10L,
                140L,
                3 * SINGLE_SLASHING_REWARD,
                2 * SINGLE_SLASHING_REWARD));
    verify(blockProcessorAltair, times(10)).processAttestation(any(), any(), any());
  }

  @Test
  void test_getProposerReward() {
    final BeaconState state = data.randomBeaconState();
    final long participantReward = spec.getSyncCommitteeParticipantReward(state).longValue();
    final long proposerWeight = PROPOSER_WEIGHT.longValue();
    final long weightDenominator = WEIGHT_DENOMINATOR.longValue();
    final long expected = participantReward * proposerWeight / (weightDenominator - proposerWeight);

    assertThat(calculator.getProposerReward(state)).isEqualTo(expected);
  }

  @Test
  void calculateProposerSlashingsRewards_shouldCalculateRewards() {
    final BeaconBlockAndState blockAndState = data.randomBlockAndStateWithValidatorLogic(16);
    final long result =
        calculator.calculateProposerSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    assertThat(result).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void calculateAttesterSlashingsRewards_shouldCalculateRewards() {
    final BeaconBlockAndState blockAndState = data.randomBlockAndStateWithValidatorLogic(100);
    final long result =
        calculator.calculateAttesterSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    assertThat(result).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void calculateProposerSyncAggregateBlockRewards_manySyncAggregateIndices() {
    final long reward = 1234L;
    final int[] participantIndices = new int[] {0, 3, 4, 7, 16, 17, 20, 23, 25, 26, 29, 30};
    final SyncAggregate syncAggregate = data.randomSyncAggregate(participantIndices);

    final long syncAggregateBlockRewards =
        calculator.calculateProposerSyncAggregateBlockRewards(reward, syncAggregate);
    assertThat(syncAggregateBlockRewards).isEqualTo(reward * participantIndices.length);
  }

  @Test
  void getBlockRewardData_shouldRejectPreAltair() {
    final BlockRewardCalculatorUtil rewardCalculator =
        new BlockRewardCalculatorUtil(TestSpecFactory.createMinimalPhase0());
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
    final BlockContainer blockContainer = mock(BlockContainer.class);
    when(blockContainer.getBlock()).thenReturn(dataStructureUtil.randomBeaconBlock());
    assertThatThrownBy(
            () -> rewardCalculator.getBlockRewardData(blockContainer, mock(BeaconState.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is pre altair,");
  }
}
