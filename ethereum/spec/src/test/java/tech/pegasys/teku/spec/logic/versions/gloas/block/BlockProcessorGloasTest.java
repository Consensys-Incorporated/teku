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

package tech.pegasys.teku.spec.logic.versions.gloas.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigGloas config = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());
  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

  private BlockProcessorGloas blockProcessor() {
    return (BlockProcessorGloas) spec.getGenesisSpec().getBlockProcessor();
  }

  @Test
  void removeBuilderPendingPayment_clearsPaymentWhenSlashedValidatorIsThePaymentProposer() {
    // header slot in the current epoch (epoch 2)
    final UInt64 headerSlot = UInt64.valueOf(2L * slotsPerEpoch + 1);
    final UInt64 proposerIndex = UInt64.valueOf(3);
    final int paymentIndex = slotsPerEpoch + headerSlot.mod(slotsPerEpoch).intValue();

    final BeaconState state =
        stateWithPaymentAt(headerSlot, paymentIndex, paymentWithProposer(proposerIndex));
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(headerSlot, proposerIndex);

    final BeaconState result =
        state.updated(mutable -> blockProcessor().removeBuilderPendingPayment(slashing, mutable));

    assertThat(builderPaymentAt(result, paymentIndex)).isEqualTo(paymentSchema().getDefault());
  }

  @Test
  void removeBuilderPendingPayment_keepsPaymentWhenSlashedValidatorIsNotThePaymentProposer() {
    final UInt64 headerSlot = UInt64.valueOf(2L * slotsPerEpoch + 1);
    final UInt64 paymentProposer = UInt64.valueOf(3);
    final UInt64 slashedProposer = UInt64.valueOf(7);
    final int paymentIndex = slotsPerEpoch + headerSlot.mod(slotsPerEpoch).intValue();

    final BuilderPendingPayment payment = paymentWithProposer(paymentProposer);
    final BeaconState state = stateWithPaymentAt(headerSlot, paymentIndex, payment);
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(headerSlot, slashedProposer);

    final BeaconState result =
        state.updated(mutable -> blockProcessor().removeBuilderPendingPayment(slashing, mutable));

    assertThat(builderPaymentAt(result, paymentIndex)).isEqualTo(payment);
  }

  @Test
  void processExecutionPayloadBid_shouldRejectWhenBlockHashEqualsParentBlockHash() {
    final UInt64 currentSlot = UInt64.valueOf(8);
    final MutableBeaconStateGloas mutableState =
        BeaconStateGloas.required(dataStructureUtil.randomBeaconState(currentSlot))
            .createWritableCopy();
    final Bytes32 parentBlockHash = mutableState.getLatestBlockHash();
    final ExecutionPayloadBid bid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .create(
                parentBlockHash,
                spec.getBlockRootAtSlot(mutableState, mutableState.getSlot().minusMinZero(1)),
                parentBlockHash,
                spec.getRandaoMix(mutableState, spec.getCurrentEpoch(mutableState)),
                dataStructureUtil.randomBytes20(),
                UInt64.ZERO,
                SpecConfigGloas.BUILDER_INDEX_SELF_BUILD,
                mutableState.getSlot(),
                UInt64.ZERO,
                UInt64.ZERO,
                schemaDefinitions.getExecutionPayloadBidSchema().getBlobKzgCommitmentsSchema().of(),
                dataStructureUtil.randomBytes32());
    final SignedExecutionPayloadBid signedBid =
        schemaDefinitions.getSignedExecutionPayloadBidSchema().create(bid, BLSSignature.infinity());

    assertThatThrownBy(() -> blockProcessor().processExecutionPayloadBid(mutableState, signedBid))
        .isInstanceOf(BlockProcessingException.class)
        .hasMessage("Bid's block hash is the same as the parent block hash");
  }

  @Test
  void processAttestation_shouldUseExplicitParentSlotForRewards() {
    final MismatchedParentFixture fixture = mismatchedParentFixture();

    final AttestationProcessingResult result =
        blockProcessor()
            .processAttestation(
                fixture.state(),
                fixture.attestation(),
                fixture.indexedAttestationProvider(),
                fixture.parentSlot());

    assertThat(result.proposerReward()).isPresent();
    assertThat(
            miscHelpersAltair()
                .hasFlag(
                    fixture.state().getCurrentEpochParticipation().get(0).get(),
                    ParticipationFlags.TIMELY_HEAD_FLAG_INDEX))
        .isTrue();
  }

  @Test
  void processAttestation_shouldUseLatestBlockHeaderParentSlotForConveniencePath() {
    final MismatchedParentFixture fixture = mismatchedParentFixture();

    final AttestationProcessingResult result =
        blockProcessor()
            .processAttestation(
                fixture.state(), fixture.attestation(), fixture.indexedAttestationProvider());

    assertThat(result.proposerReward()).isPresent();
    assertThat(
            miscHelpersAltair()
                .hasFlag(
                    fixture.state().getCurrentEpochParticipation().get(0).get(),
                    ParticipationFlags.TIMELY_HEAD_FLAG_INDEX))
        .isTrue();
  }

  private MismatchedParentFixture mismatchedParentFixture() {
    final UInt64 parentSlot = UInt64.valueOf(8);
    final UInt64 dataSlot = parentSlot.plus(1);
    final UInt64 stateSlot = dataSlot.plus(1);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slotsPerHistoricalRoot = UInt64.valueOf(config.getSlotsPerHistoricalRoot());
    final MutableBeaconStateGloas state =
        BeaconStateGloas.required(dataStructureUtil.randomBeaconState(stateSlot))
            .createWritableCopy();
    state.setLatestBlockHeader(
        new BeaconBlockHeader(parentSlot, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO));
    state.getBlockRoots().setElement(parentSlot.mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    state.getBlockRoots().setElement(dataSlot.mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    state.setCurrentJustifiedCheckpoint(
        new Checkpoint(spec.computeEpochAtSlot(dataSlot), blockRoot));
    state.getCurrentEpochParticipation().set(0, SszByte.asUInt8(0));
    // Deliberately different from the header slot so the test fails if the bid slot is used
    final UInt64 staleBidSlot = parentSlot.minus(1);
    state.setLatestExecutionPayloadBid(
        dataStructureUtil.randomExecutionPayloadBid(staleBidSlot, UInt64.ZERO));
    state.setExecutionPayloadAvailability(
        schemaDefinitions
            .getExecutionPayloadAvailabilitySchema()
            .ofBits(parentSlot.mod(slotsPerHistoricalRoot).intValue()));

    final AttestationData data =
        new AttestationData(
            dataSlot,
            UInt64.ONE,
            blockRoot,
            new Checkpoint(spec.computeEpochAtSlot(dataSlot), blockRoot),
            new Checkpoint(spec.computeEpochAtSlot(dataSlot), blockRoot));
    final Attestation attestation = dataStructureUtil.randomAttestation(data);
    final IndexedAttestationLight indexedAttestation =
        new IndexedAttestationLight(List.of(UInt64.ZERO), data, BLSSignature.infinity());
    final AbstractBlockProcessor.IndexedAttestationProvider indexedAttestationProvider =
        ignored -> indexedAttestation;
    return new MismatchedParentFixture(state, attestation, indexedAttestationProvider, parentSlot);
  }

  private MiscHelpersAltair miscHelpersAltair() {
    return spec.getGenesisSpec().miscHelpers().toVersionAltair().orElseThrow();
  }

  private record MismatchedParentFixture(
      MutableBeaconStateGloas state,
      Attestation attestation,
      AbstractBlockProcessor.IndexedAttestationProvider indexedAttestationProvider,
      UInt64 parentSlot) {}

  private BeaconState stateWithPaymentAt(
      final UInt64 slot, final int paymentIndex, final BuilderPendingPayment payment) {
    return dataStructureUtil
        .randomBeaconState(slot)
        .updated(
            mutable ->
                MutableBeaconStateGloas.required(mutable)
                    .getBuilderPendingPayments()
                    .set(paymentIndex, payment));
  }

  private BuilderPendingPayment paymentWithProposer(final UInt64 proposerIndex) {
    return paymentSchema()
        .create(
            UInt64.valueOf(1000),
            dataStructureUtil.randomBuilderPendingWithdrawal(),
            proposerIndex);
  }

  private BuilderPendingPayment builderPaymentAt(final BeaconState state, final int index) {
    return BeaconStateGloas.required(state).getBuilderPendingPayments().get(index);
  }

  private BuilderPendingPaymentSchema paymentSchema() {
    return spec.getGenesisSchemaDefinitions()
        .toVersionGloas()
        .orElseThrow()
        .getBuilderPendingPaymentSchema();
  }
}
