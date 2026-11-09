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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.SAVE_FOR_FUTURE;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

public class Phase0AttestationValidatorTest extends AbstractAttestationValidatorTest {

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalPhase0(configAdapter);
  }

  @Test
  public void shouldReturnValidForValidAttestation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWhenBlockBeingVotedForHasFailedValidation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    invalidBlockRoots.put(
        attestation.getData().getBeaconBlockRoot(),
        BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
    assertThat(validate(attestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreAttestationWhenFinalizedCheckpointIsNotAncestorOfBlock() {
    final StateAndBlockSummary head = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(head);
    final GossipValidationHelper gossipValidationHelper =
        spy(new GossipValidationHelper(spec, recentChainData, new StubMetricsSystem()));
    doReturn(false)
        .when(gossipValidationHelper)
        .currentFinalizedCheckpointIsAncestorOfAttestationBlock(
            attestation.getData().getBeaconBlockRoot());
    final AttestationValidator validator =
        new AttestationValidator(
            spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots);

    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(
                    spec,
                    attestation,
                    spec.computeSubnetForAttestation(head.getState(), attestation))))
        .isCompletedWithValueMatching(
            result ->
                result.isIgnore()
                    && result
                        .getDescription()
                        .orElse("")
                        .contains("Finalized checkpoint is not an ancestor of block"));
  }

  @Test
  public void shouldReturnValidForValidAttestation_whenManyBlocksHaveBeenSkipped() {
    final StateAndBlockSummary head = storageSystem.getChainHead();
    final UInt64 currentSlot = head.getSlot().plus(spec.getSlotsPerEpoch(head.getSlot()) * 3L);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final Attestation attestation =
        attestationGenerator.validAttestation(
            head, head.getSlot().plus(spec.getSlotsPerEpoch(head.getSlot()) * 3L));
    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithIncorrectAggregateBitsSize() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    final SszBitlist validAggregationBits = attestation.getAggregationBits();
    SszBitlist invalidAggregationBits =
        validAggregationBits
            .getSchema()
            .ofBits(validAggregationBits.size() + 1)
            .or(validAggregationBits);
    final Attestation invalidAttestation =
        attestationSchema.create(
            invalidAggregationBits, attestation.getData(), attestation.getAggregateSignature());
    assertThat(validate(invalidAttestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreAttestationFromBeforeAttestationPropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    // In the first slot after
    final UInt64 slot =
        UInt64.valueOf(spec.getNetworkingConfig().getAttestationPropagationSlotRange()).increment();
    chainUpdater.setCurrentSlot(slot);
    // Add one more second to get past the MAXIMUM_GOSSIP_CLOCK_DISPARITY
    chainUpdater.setTime(recentChainData.getStore().getTimeSeconds().plus(ONE));

    assertThat(validate(attestation).code()).isEqualTo(IGNORE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfEarliestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    // At the very start of the first slot the attestation isn't allowed, but still within
    // the MAXIMUM_GOSSIP_CLOCK_DISPARITY so should be allowed.
    final UInt64 slot =
        UInt64.valueOf(spec.getNetworkingConfig().getAttestationPropagationSlotRange()).increment();
    chainUpdater.setCurrentSlot(slot);

    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldDeferAttestationFromAfterThePropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    chainUpdater.setCurrentSlot(ZERO);

    assertThat(validate(attestation).code()).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfLatestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    // Ideally we'd rewind the time by a few milliseconds but our Store only keeps time to second
    // precision.  Alternatively we might consider using system time, not store time.
    chainUpdater.setCurrentSlot(ONE);

    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAggregatedAttestation() {
    final Attestation attestation =
        AttestationGenerator.groupAndAggregateAttestations(
                attestationGenerator.getAttestationsForSlot(storageSystem.getChainHead()))
            .get(0);

    assertThat(validate(attestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldAcceptAttestationForSameValidatorButDifferentTargetEpoch() {
    final StateAndBlockSummary genesis = storageSystem.getChainHead();
    final SignedBeaconBlock nextEpochBlock =
        chainUpdater
            .advanceChain(UInt64.valueOf(spec.getSlotsPerEpoch(genesis.getSlot()) + 1))
            .getBlock();

    // Slot 0 attestation
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis);

    // Slot 1 attestation from the same validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(storageSystem.getChainHead(), nextEpochBlock.getSlot())
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getTarget().getEpoch())
        .isNotEqualTo(attestation2.getData().getTarget().getEpoch());
    assertThat(attestation1.getAggregationBits()).isEqualTo(attestation2.getAggregationBits());

    assertThat(validate(attestation1).code()).isEqualTo(ACCEPT);
    assertThat(validate(attestation2).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldAcceptAttestationForSameSlotButDifferentValidator() {
    final StateAndBlockSummary genesis = storageSystem.getChainHead();

    // Slot 0 attestation from one validator
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis, ZERO);

    // Slot 0 attestation from a different validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(genesis, ZERO)
            .filter(attestation -> !hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getSlot()).isEqualTo(attestation2.getData().getSlot());
    assertThat(attestation1.getAggregationBits()).isNotEqualTo(attestation2.getAggregationBits());

    assertThat(validate(attestation1).code()).isEqualTo(ACCEPT);
    assertThat(validate(attestation2).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithInvalidSignature() {
    final Attestation attestation =
        attestationGenerator.attestationWithInvalidSignature(storageSystem.getChainHead());

    assertThat(validate(attestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldDeferAttestationWhenBlockBeingVotedForIsNotAvailable() {
    final BeaconBlockAndState unknownBlockAndState =
        chainBuilder.generateBlockAtSlot(ONE).toUnsigned();
    chainUpdater.setCurrentSlot(ONE);
    final Attestation attestation = attestationGenerator.validAttestation(unknownBlockAndState);

    assertThat(validate(attestation).code()).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldRejectAttestationsSentOnTheWrongSubnet() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(spec, attestation, expectedSubnetId + 1)))
        .matches(rejected("Attestation received on incorrect subnet"), "Rejected incorrect subnet");

    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(spec, attestation, expectedSubnetId)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  public void shouldRejectAttestationsWithCommitteeIndexNotInTheExpectedRange() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            spec.getCommitteeCountPerSlot(
                                blockAndState.getState(), data.getTarget().getEpoch()),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            data.getTarget()),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(rejected("Committee index 2 is out of range"), "Rejected committee out of range");
  }

  @Test
  public void shouldRejectAttestationsThatHaveNonMatchingTargetEpochAndSlot() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            data.getIndex(),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            new Checkpoint(data.getTarget().getEpoch().plus(2), Bytes32.ZERO)),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(rejected("is not from target epoch"), "Rejected with is not from target epoch");
  }

  @Test
  public void shouldRejectAttestationsThatHaveLMDVotesInconsistentWithTargetRoot() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
    final AttestationValidator validator =
        new AttestationValidator(
            spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots);
    final AttestationData data = attestation.getData();
    final Checkpoint checkpoint =
        new Checkpoint(
            data.getTarget().getEpoch().minusMinZero(1),
            blockAndState.getBeaconBlock().orElseThrow().getParentRoot());
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);

    // the signature would be invalid, but it's actually not the point of the test case.
    when(signatureVerifier.verify(anyList(), any(Bytes.class), any()))
        .thenReturn(SafeFuture.completedFuture(true));
    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            data.getIndex(),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            checkpoint),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(
            rejected("descend from target block"), "Rejected does not descend from target block");
  }

  @Test
  public void shouldRejectFutureSlotAttestationWithInvalidSignature() {
    // A future-slot attestation with a bogus signature must be rejected (so the sender is
    // penalised) rather than deferred with a penalty-free Ignore verdict, which would force a
    // signature verification in the fork choice path for free.
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState, ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
    when(signatureVerifier.verify(anyList(), any(Bytes.class), any()))
        .thenReturn(SafeFuture.completedFuture(false));
    final AttestationValidator validator =
        new AttestationValidator(
            spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots);

    final int subnetId = spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    chainUpdater.setCurrentSlot(ZERO);

    assertThat(
            validator
                .validate(ValidatableAttestation.fromNetwork(spec, attestation, subnetId))
                .join()
                .code())
        .isEqualTo(REJECT);
    // The signature was actually verified at gossip time, giving a REJECT verdict that penalises
    // the sender.
    verify(signatureVerifier).verify(anyList(), any(Bytes.class), any());
  }

  @Test
  public void shouldVerifyAndCacheSignatureForValidFutureSlotAttestation() {
    // A valid future-slot attestation is still only deferred (SAVE_FOR_FUTURE), but its signature
    // is verified and cached here so the fork choice path does not re-verify it.
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState, ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);
    chainUpdater.setCurrentSlot(ZERO);

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.fromNetwork(
            spec,
            attestation,
            spec.computeSubnetForAttestation(blockAndState.getState(), attestation));

    assertThat(validator.validate(validatableAttestation).join().code()).isEqualTo(SAVE_FOR_FUTURE);
    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
  }

  @Test
  public void shouldRejectFutureSlotAttestationOnWrongSubnetWithoutVerifyingSignature() {
    // Like an in-window attestation (see shouldRejectAttestationsSentOnTheWrongSubnet), and checked
    // before the signature so that a valid attestation replayed on other subnets doesn't cost a BLS
    // verification for each copy
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState, ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);
    final int subnetId = spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
    final AttestationValidator validator =
        new AttestationValidator(
            spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots);
    chainUpdater.setCurrentSlot(ZERO);

    assertThat(
            validator.validate(ValidatableAttestation.fromNetwork(spec, attestation, subnetId + 1)))
        .matches(rejected("Attestation received on incorrect subnet"), "Rejected incorrect subnet");
    verifyNoInteractions(signatureVerifier);
  }

  @Test
  public void shouldRejectFutureSlotAttestationWithCommitteeIndexNotInTheExpectedRange() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState, ONE);
    final AttestationData data = attestation.getData();
    final int subnetId = spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    chainUpdater.setCurrentSlot(ZERO);

    assertThat(
            validator.validate(
                ValidatableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            spec.getCommitteeCountPerSlot(
                                blockAndState.getState(), data.getTarget().getEpoch()),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            data.getTarget()),
                        attestation.getAggregateSignature()),
                    subnetId)))
        .matches(rejected("Committee index 2 is out of range"), "Rejected committee out of range");
  }
}
