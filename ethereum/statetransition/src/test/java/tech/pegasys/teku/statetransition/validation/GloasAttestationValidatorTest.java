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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AggregateGenerator;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.signatures.LocalSigner;

public class GloasAttestationValidatorTest extends ElectraAttestationValidatorTest {

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalGloas(configAdapter);
  }

  @Override
  @Test
  @Disabled(
      "This validation is applicable to Electra only. In Gloas, an index of 0 is a valid payload status.")
  public void shouldRejectAggregateWithAttestationDataIndexNonZero() {}

  @Override
  @Test
  @Disabled(
      "This validation is applicable to Electra only. In Gloas, an index of 0 is a valid payload status.")
  public void shouldRejectSingleAttestationWithAttestationDataIndexNonZero() {}

  @Test
  public void shouldRejectAggregateWithAttestationDataWithInvalidPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.valueOf(2),
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature(),
                attestation::getCommitteeBitsRequired);

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isGreaterThan(UInt64.ONE);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 or 1 for Gloas, but was %s.",
                wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectSingleAttestationWithInvalidPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.valueOf(2),
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(
                UInt64.ONE,
                UInt64.ONE,
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature());

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isGreaterThan(UInt64.ONE);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Attestation data index must be 0 or 1 for Gloas, but was %s.",
                wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldRejectSingleAttestationWithIncorrectPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            ONE,
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(
                UInt64.ONE,
                UInt64.ONE,
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature());

    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(ZERO);
    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Payload status must be 0, but was %s.", wrongAttestation.getData().getIndex()));
  }

  @Test
  public void shouldSaveFullPayloadAttestationForFutureWhenPayloadHasNotBeenSeen() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = signedFullPayloadVoteForPastBlock(blockAndState);
    final AttestationValidator validator = validatorWithPayloadNotSeen(blockAndState.getRoot());
    chainUpdater.setCurrentSlot(attestation.getData().getSlot());

    final ValidatableAttestation validatableAttestation = fromNetwork(blockAndState, attestation);
    assertThat(validator.validate(validatableAttestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
    // The signature is verified before deferring, and cached so fork choice doesn't verify it again
    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
  }

  @Test
  public void shouldRejectFullPayloadAttestationWithInvalidSignatureWhenPayloadHasNotBeenSeen() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = fullPayloadVoteWithInvalidSignature(blockAndState);
    final AttestationValidator validator = validatorWithPayloadNotSeen(blockAndState.getRoot());
    chainUpdater.setCurrentSlot(attestation.getData().getSlot());

    assertThat(validator.validate(fromNetwork(blockAndState, attestation)))
        .matches(
            rejected("Attestation is not a valid indexed attestation"),
            "Rejected invalid signature");
  }

  @Test
  public void shouldRejectFullPayloadAttestationOnWrongSubnetWithoutVerifyingSignature() {
    // A valid vote replayed on other subnets is rejected before its signature is verified, so the
    // copies don't each cost a BLS verification
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = signedFullPayloadVoteForPastBlock(blockAndState);
    final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
    final AttestationValidator validator =
        validatorWithPayloadNotSeen(blockAndState.getRoot(), signatureVerifier);
    chainUpdater.setCurrentSlot(attestation.getData().getSlot());
    final int subnetId = spec.computeSubnetForAttestation(blockAndState.getState(), attestation);

    assertThat(
            validator.validate(ValidatableAttestation.fromNetwork(spec, attestation, subnetId + 1)))
        .matches(rejected("Attestation received on incorrect subnet"), "Rejected incorrect subnet");
    verifyNoInteractions(signatureVerifier);
  }

  @Test
  public void shouldIgnoreFullPayloadAttestationOutsidePropagationRangeWhenPayloadHasNotBeenSeen() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = signedFullPayloadVoteForPastBlock(blockAndState);
    final AttestationValidator validator = validatorWithPayloadNotSeen(blockAndState.getRoot());
    // The attestation's epoch is neither the current nor the previous epoch, even with the clock
    // disparity allowance (which still counts the start of epoch 2 as within epoch 1)
    chainUpdater.setCurrentSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(3)));

    assertThat(validator.validate(fromNetwork(blockAndState, attestation)))
        .isCompletedWithValue(InternalValidationResult.IGNORE);
  }

  @Test
  public void shouldVerifyFullPayloadAggregateBeforeSavingItForFutureWhenPayloadHasNotBeenSeen() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = signedFullPayloadVoteForPastBlock(blockAndState);
    final AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(
            spec, validatorWithPayloadNotSeen(blockAndState.getRoot()), signatureVerifier);
    chainUpdater.setCurrentSlot(attestation.getData().getSlot());

    final ValidatableAttestation validatableAggregate =
        ValidatableAttestation.aggregateFromNetwork(spec, aggregateOf(blockAndState, attestation));
    assertThat(aggregateValidator.validate(validatableAggregate))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
    assertThat(validatableAggregate.isValidIndexedAttestation()).isTrue();
  }

  @Test
  public void shouldRejectFullPayloadAggregateWithInvalidSignatureWhenPayloadHasNotBeenSeen() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = fullPayloadVoteWithInvalidSignature(blockAndState);
    final AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(
            spec, validatorWithPayloadNotSeen(blockAndState.getRoot()), signatureVerifier);
    chainUpdater.setCurrentSlot(attestation.getData().getSlot());

    // The selection proof and aggregator signature are valid, only the aggregate signature is not
    assertThat(
            aggregateValidator.validate(
                ValidatableAttestation.aggregateFromNetwork(
                    spec, aggregateOf(blockAndState, attestation))))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  public void shouldRejectFullPayloadAttestationWhenPayloadIsKnownInvalid() {
    final Bytes32 blockRoot = Bytes32.random();
    final Attestation attestation = fullPayloadAttestationForPastBlock(blockRoot);
    final GossipValidationHelper gossipValidationHelper =
        mockGossipValidationForFullPayloadRule(attestation, blockRoot);
    final Set<Bytes32> blockRootsWithInvalidExecutionPayload = new HashSet<>();
    blockRootsWithInvalidExecutionPayload.add(blockRoot);

    final AttestationValidator validator =
        new AttestationValidator(
            spec,
            signatureVerifier,
            gossipValidationHelper,
            invalidBlockRoots,
            blockRootsWithInvalidExecutionPayload);

    assertThat(validator.validate(ValidatableAttestation.fromNetwork(spec, attestation, 0)))
        .matches(
            rejected("Execution payload for full payload attestation is invalid"),
            "Rejected invalid execution payload");
  }

  @Test
  public void shouldRejectAggregateAttestationWithIncorrectPayloadStatus() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    final AttestationData correctAttestationData = attestation.getData();

    final AttestationData invalidPayloadStatusValueData =
        new AttestationData(
            correctAttestationData.getSlot(),
            UInt64.ONE,
            correctAttestationData.getBeaconBlockRoot(),
            correctAttestationData.getSource(),
            correctAttestationData.getTarget());

    final Attestation wrongAttestation =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                invalidPayloadStatusValueData,
                attestation.getAggregateSignature(),
                attestation::getCommitteeBitsRequired);

    // Sanity check
    assertThat(wrongAttestation.getData().getIndex()).isNotEqualTo(UInt64.ZERO);

    assertThat(validate(wrongAttestation))
        .isEqualTo(
            InternalValidationResult.reject(
                "Payload status must be 0, but was %s.", wrongAttestation.getData().getIndex()));
  }

  /**
   * A full payload vote (attestation.data.index == 1) for the given block, from the slot after it,
   * correctly signed by its attester.
   */
  private Attestation signedFullPayloadVoteForPastBlock(final StateAndBlockSummary blockAndState) {
    final Attestation attestation =
        attestationGenerator.validAttestation(blockAndState, blockAndState.getSlot().plus(ONE));
    final AttestationData data = attestation.getData();
    // Sanity check: a full payload vote must be for a block from an earlier slot
    assertThat(data.getSlot()).isGreaterThan(blockAndState.getSlot());
    final AttestationData fullPayloadData =
        new AttestationData(
            data.getSlot(), ONE, data.getBeaconBlockRoot(), data.getSource(), data.getTarget());
    final BeaconState state = blockAndState.getState();
    final UInt64 attesterIndex = spec.getAttestingIndices(state, attestation).getFirst();
    final BLSSignature signature =
        new LocalSigner(
                spec,
                chainBuilder.getValidatorKeys().get(attesterIndex.intValue()),
                SyncAsyncRunner.SYNC_RUNNER)
            .signAttestationData(fullPayloadData, state.getForkInfo())
            .join();
    return attestationSchema.create(
        attestation.getAggregationBits(),
        fullPayloadData,
        signature,
        attestation::getCommitteeBitsRequired);
  }

  private Attestation fullPayloadVoteWithInvalidSignature(
      final StateAndBlockSummary blockAndState) {
    final Attestation validVote = signedFullPayloadVoteForPastBlock(blockAndState);
    // A well-formed signature, but over the attestation data with a different payload status
    final BLSSignature wrongSignature =
        attestationGenerator
            .validAttestation(blockAndState, validVote.getData().getSlot())
            .getAggregateSignature();
    return attestationSchema.create(
        validVote.getAggregationBits(),
        validVote.getData(),
        wrongSignature,
        validVote::getCommitteeBitsRequired);
  }

  private AttestationValidator validatorWithPayloadNotSeen(final Bytes32 blockRoot) {
    return validatorWithPayloadNotSeen(blockRoot, signatureVerifier);
  }

  private AttestationValidator validatorWithPayloadNotSeen(
      final Bytes32 blockRoot, final AsyncBLSSignatureVerifier signatureVerifier) {
    final GossipValidationHelper gossipValidationHelper =
        spy(new GossipValidationHelper(spec, recentChainData, new StubMetricsSystem()));
    doReturn(Optional.empty())
        .when(gossipValidationHelper)
        .getRecentlyImportedExecutionPayload(blockRoot);
    return new AttestationValidator(
        spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots, Set.of());
  }

  private ValidatableAttestation fromNetwork(
      final StateAndBlockSummary blockAndState, final Attestation attestation) {
    return ValidatableAttestation.fromNetwork(
        spec, attestation, spec.computeSubnetForAttestation(blockAndState.getState(), attestation));
  }

  private SignedAggregateAndProof aggregateOf(
      final StateAndBlockSummary blockAndState, final Attestation attestation) {
    return new AggregateGenerator(spec, chainBuilder.getValidatorKeys())
        .generator()
        .blockAndState(blockAndState, attestation.getData().getSlot())
        .aggregate(attestation)
        .generate();
  }

  private GossipValidationHelper mockGossipValidationForFullPayloadRule(
      final Attestation attestation, final Bytes32 blockRoot) {
    final GossipValidationHelper gossipValidationHelper =
        spy(new GossipValidationHelper(spec, recentChainData, new StubMetricsSystem()));
    doReturn(Optional.of(ZERO)).when(gossipValidationHelper).getSlotForBlockRoot(blockRoot);
    doReturn(ZERO).when(gossipValidationHelper).getGenesisTime();
    doReturn(spec.computeTimeMillisAtSlot(attestation.getData().getSlot(), ZERO))
        .when(gossipValidationHelper)
        .getCurrentTimeMillis();
    doReturn(true).when(gossipValidationHelper).isBlockAvailable(blockRoot);
    return gossipValidationHelper;
  }

  private Attestation fullPayloadAttestationForPastBlock(final Bytes32 blockRoot) {
    final AttestationData data =
        new AttestationData(
            ONE,
            ONE,
            blockRoot,
            new Checkpoint(ZERO, Bytes32.ZERO),
            new Checkpoint(spec.computeEpochAtSlot(ONE), Bytes32.ZERO));
    return attestationSchema.create(
        attestationSchema.getAggregationBitsSchema().ofBits(1, 0),
        data,
        BLSSignature.empty(),
        () -> attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(1));
  }
}
