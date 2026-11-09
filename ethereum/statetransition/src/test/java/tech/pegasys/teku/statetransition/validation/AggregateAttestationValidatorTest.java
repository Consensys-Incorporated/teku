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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AggregateGenerator;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * The following validations MUST pass before forwarding the signed_aggregate_and_proof on the
 * network. (We define the following for convenience -- aggregate_and_proof =
 * signed_aggregate_and_proof.message and aggregate = aggregate_and_proof.aggregate)
 *
 * <ul>
 *   <li>Phase 0
 *       <p>aggregate.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. aggregate.data.slot +
 *       ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= aggregate.data.slot (a client MAY
 *       queue future aggregates for processing at the appropriate slot).
 *   <li>Deneb
 *       <p>aggregate.data.slot is equal to or earlier than the current_slot (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. aggregate.data.slot <= current_slot (a
 *       client MAY queue future aggregates for processing at the appropriate slot).
 *       <p>the epoch of aggregate.data.slot is either the current or previous epoch (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e.
 *       compute_epoch_at_slot(aggregate.data.slot) in (get_previous_epoch(state),
 *       get_current_epoch(state))
 * </ul>
 *
 * <p>The aggregate attestation defined by hash_tree_root(aggregate) has not already been seen (via
 * aggregate gossip, within a block, or through the creation of an equivalent aggregate locally).
 *
 * <p>The aggregate is the first valid aggregate received for the aggregator with index
 * aggregate_and_proof.aggregator_index for the slot aggregate.data.slot.
 *
 * <p>The block being voted for (aggregate.data.beacon_block_root) passes validation.
 *
 * <p>aggregate_and_proof.selection_proof selects the validator as an aggregator for the slot --
 * i.e. is_aggregator(state, aggregate.data.slot, aggregate.data.index,
 * aggregate_and_proof.selection_proof) returns True.
 *
 * <p>The aggregator's validator index is within the aggregate's committee -- i.e.
 * aggregate_and_proof.aggregator_index in get_attesting_indices(state, aggregate.data,
 * aggregate.aggregation_bits).
 *
 * <p>The aggregate_and_proof.selection_proof is a valid signature of the aggregate.data.slot by the
 * validator with index aggregate_and_proof.aggregator_index.
 *
 * <p>The aggregator signature, signed_aggregate_and_proof.signature, is valid.
 *
 * <p>The signature of aggregate is valid.
 */
@TestSpecContext(
    milestone = {PHASE0, ELECTRA},
    signatureVerifierNoop = true)
class AggregateAttestationValidatorTest {

  private Spec spec;
  private SpecVersion genesisSpec;
  private DataStructureUtil dataStructureUtil;
  private SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private AggregateAndProofSchema aggregateAndProofSchema;
  private StorageSystem storageSystem;
  private ChainUpdater chainUpdater;
  private AttestationValidator attestationValidator;
  private AggregateGenerator generator;

  private AggregateAttestationValidator validator;
  private SignedBlockAndState bestBlock;
  private SignedBlockAndState genesis;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    genesisSpec = spec.getGenesisSpec();
    signedAggregateAndProofSchema =
        specContext.getSchemaDefinitions().getSignedAggregateAndProofSchema();
    aggregateAndProofSchema = specContext.getSchemaDefinitions().getAggregateAndProofSchema();
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .numberOfValidators(1024)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();

    final ChainBuilder chainBuilder = storageSystem.chainBuilder();
    chainUpdater = storageSystem.chainUpdater();
    generator = new AggregateGenerator(spec, chainBuilder.getValidatorKeys());

    attestationValidator = mock(AttestationValidator.class);
    final AsyncBLSSignatureVerifier signatureVerifier =
        AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);

    validator = new AggregateAttestationValidator(spec, attestationValidator, signatureVerifier);

    genesis = chainUpdater.initializeGenesis(false);
    bestBlock = chainUpdater.addNewBestBlock();
  }

  private void disableSignatureVerification() {
    validator =
        new AggregateAttestationValidator(
            spec, attestationValidator, AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP));
  }

  private void useRealAttestationValidator() {
    final AsyncBLSSignatureVerifier signatureVerifier =
        AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);
    validator =
        new AggregateAttestationValidator(
            spec,
            new AttestationValidator(
                spec,
                signatureVerifier,
                new GossipValidationHelper(
                    spec, storageSystem.recentChainData(), new StubMetricsSystem()),
                Map.of()),
            signatureVerifier);
  }

  @TestTemplate
  public void shouldReturnValidForValidAggregate() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregate = generator.validAggregateAndProof(chainHead);
    whenAttestationIsValid(aggregate);
    assertThat(validator.validate(ValidatableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void shouldRejectWhenAttestationValidatorRejects() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResultWithState.reject("Nah mate")));

    assertThat(validator.validate(attestation)).isCompletedWithValue(reject("Nah mate"));
  }

  @TestTemplate
  public void shouldRejectElectraAggregateWithNoCommitteeBitsBeforeSeenAggregateChecks(
      final SpecContext specContext) {
    specContext.assumeIsOneOf(ELECTRA);

    final SignedAggregateAndProof validSignedAggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final AggregateAndProof validAggregateAndProof = validSignedAggregate.getMessage();
    final Attestation validAggregate = validAggregateAndProof.getAggregate();
    final Attestation aggregateWithNoCommitteeBits =
        aggregateAndProofSchema
            .getAttestationSchema()
            .create(
                validAggregate.getAggregationBits(),
                validAggregate.getData(),
                validAggregate.getAggregateSignature(),
                () -> validAggregate.getSchema().getCommitteeBitsSchema().orElseThrow().ofBits());
    final SignedAggregateAndProof signedAggregate =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(
                validAggregateAndProof.getIndex(),
                aggregateWithNoCommitteeBits,
                validAggregateAndProof.getSelectionProof()),
            validSignedAggregate.getSignature());

    assertThat(aggregateWithNoCommitteeBits.getCommitteeBitsRequired().getBitCount()).isZero();
    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, signedAggregate)))
        .isCompletedWithValue(
            reject("Rejecting attestation because committee bits count is not 1"));
  }

  @TestTemplate
  public void shouldIgnoreWhenAttestationValidatorIgnores() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.ignore()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.IGNORE);
  }

  @TestTemplate
  public void shouldSaveForFutureWhenAttestationValidatorSavesForFuture() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @TestTemplate
  public void shouldSaveForFutureWhenStateIsNotAvailable() {
    final SignedBlockAndState target = bestBlock;
    final SignedAggregateAndProof aggregate = generator.validAggregateAndProof(target.toUnsigned());
    ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @TestTemplate
  public void shouldSaveForFutureValidAggregateWhenAttestationValidatorSavesForFutureWithState() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    whenAttestationIsSavedForFuture(aggregate);

    assertThat(validator.validate(ValidatableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @TestTemplate
  public void shouldRejectFutureAggregateIfAggregateAndProofSignatureIsNotValid() {
    final SignedAggregateAndProof validAggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final SignedAggregateAndProof invalidAggregate =
        signedAggregateAndProofSchema.create(
            validAggregate.getMessage(), dataStructureUtil.randomSignature());
    whenAttestationIsSavedForFuture(invalidAggregate);

    // The aggregate checks and signatures are verified before a future aggregate is deferred
    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, invalidAggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  public void shouldNotMarkFutureAggregateAsSeen() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    final BeaconState state = getStateFor(aggregate).orElseThrow();
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture(state)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.accept(state)));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
    // Once its slot arrives, the same aggregate is accepted rather than ignored as a duplicate
    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void shouldVerifyFutureSlotAggregateBeforeSavingItForFuture() {
    useRealAttestationValidator();
    chainUpdater.setCurrentSlot(ONE);
    final UInt64 futureSlot = UInt64.valueOf(2);
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(bestBlock.toUnsigned(), futureSlot);
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromNetwork(spec, aggregate);

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
    // The attestation signature is verified and cached, so fork choice doesn't verify it again
    assertThat(attestation.isValidIndexedAttestation()).isTrue();

    // Once its slot arrives, the same aggregate is accepted rather than ignored as a duplicate
    chainUpdater.setCurrentSlot(futureSlot);
    assertThat(validator.validate(ValidatableAttestation.aggregateFromNetwork(spec, aggregate)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void shouldRejectFutureSlotAggregateWithInvalidAttestationSignature() {
    useRealAttestationValidator();
    chainUpdater.setCurrentSlot(ONE);
    final SignedAggregateAndProof aggregate =
        aggregateWithInvalidAttestationSignature(bestBlock.toUnsigned(), UInt64.valueOf(2));

    assertThat(validator.validate(ValidatableAttestation.aggregateFromNetwork(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  public void shouldVerifyAggregateForUnknownBlockBeforeSavingItForFuture() {
    useRealAttestationValidator();
    final SignedBlockAndState unknownBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(UInt64.valueOf(2));
    chainUpdater.setCurrentSlot(unknownBlock.getSlot());
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromNetwork(
            spec, generator.validAggregateAndProof(unknownBlock.toUnsigned()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
    // Verified against the target checkpoint state and cached, so fork choice doesn't verify it
    // again once the block is imported
    assertThat(attestation.isValidIndexedAttestation()).isTrue();
  }

  @TestTemplate
  public void shouldRejectAggregateForUnknownBlockWithInvalidAttestationSignature() {
    useRealAttestationValidator();
    final SignedBlockAndState unknownBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(UInt64.valueOf(2));
    chainUpdater.setCurrentSlot(unknownBlock.getSlot());
    final SignedAggregateAndProof aggregate =
        aggregateWithInvalidAttestationSignature(unknownBlock.toUnsigned(), unknownBlock.getSlot());

    assertThat(validator.validate(ValidatableAttestation.aggregateFromNetwork(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  /**
   * The selection proof and aggregator signature are valid, only the aggregate signature is not.
   */
  private SignedAggregateAndProof aggregateWithInvalidAttestationSignature(
      final StateAndBlockSummary blockAndState, final UInt64 slot) {
    final Attestation validAttestation =
        generator.getAttestationGenerator().validAttestation(blockAndState, slot);
    final AttestationSchema<?> attestationSchema = aggregateAndProofSchema.getAttestationSchema();
    final Attestation invalidAttestation =
        attestationSchema.create(
            validAttestation.getAggregationBits(),
            validAttestation.getData(),
            dataStructureUtil.randomSignature(),
            getCommitteeBitsSupplier(attestationSchema, validAttestation.getFirstCommitteeIndex()));
    return generator
        .generator()
        .blockAndState(blockAndState, slot)
        .aggregate(invalidAttestation)
        .generate();
  }

  @TestTemplate
  public void shouldOnlyAcceptFirstAggregateWithSameSlotAndAggregatorIndex() {
    final BeaconBlockAndState chainHead = bestBlock.toUnsigned();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final List<Attestation> aggregatesForSlot =
        AttestationGenerator.groupAndAggregateAttestations(
            generator
                .getAttestationGenerator()
                .getAttestationsForSlot(chainHead, chainHead.getSlot()));
    final Attestation aggregate2 =
        aggregatesForSlot.stream()
            .filter(attestation -> hasSameCommitteeIndex(aggregateAndProof1, attestation))
            .findFirst()
            .orElseThrow();
    final SignedAggregateAndProof aggregateAndProof2 =
        generator.generator().blockAndState(chainHead).aggregate(aggregate2).generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregate2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  public void shouldAcceptAggregateWithSameHashTreeRoot() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final Attestation attestation = aggregateAndProof1.getMessage().getAggregate();

    final SignedAggregateAndProof aggregateAndProof2 =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(UInt64.valueOf(2), attestation, BLSSignature.empty()),
            BLSSignature.empty());

    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregateAndProof2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    ValidatableAttestation attestation1 =
        ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof1);
    ValidatableAttestation attestation2 =
        ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof2);

    // Sanity check
    assertThat(attestation1.hashTreeRoot()).isEqualTo(attestation2.hashTreeRoot());

    assertThat(validator.validate(attestation1))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(validator.validate(attestation2))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  public void shouldOnlyAcceptFirstAggregateWithSameHashTreeRootWhenPassedSeenAggregates() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);
    final Attestation attestation = aggregateAndProof1.getMessage().getAggregate();
    final SignedAggregateAndProof aggregateAndProof2 =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(UInt64.valueOf(2), attestation, BLSSignature.empty()),
            BLSSignature.empty());

    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregateAndProof2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    ValidatableAttestation attestation1 =
        ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof1);
    ValidatableAttestation attestation2 =
        ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof2);

    // Sanity check
    assertThat(attestation1.hashTreeRoot()).isEqualTo(attestation2.hashTreeRoot());

    validator.addSeenAggregate(attestation1);
    assertThat(validator.validate(attestation2))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectAggregateAttestationWithNoParticipants() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof validAggregate = generator.validAggregateAndProof(chainHead);
    final AttestationData attestationData = validAggregate.getMessage().getAggregate().getData();
    final UInt64 committeeIndex =
        validAggregate.getMessage().getAggregate().getFirstCommitteeIndex();
    // all aggregation bits are set to 0b0 (no participants)
    final ValidatableAttestation attestation =
        createValidAggregate(ONE, attestationData, committeeIndex, false, false, false);

    final InternalValidationResult validationResult = safeJoin(validator.validate(attestation));

    assertThat(validationResult.isReject()).isTrue();
    assertThat(validationResult.getDescription())
        .hasValue("Rejecting aggregate attestation because it does not have participants");
  }

  @TestTemplate
  void shouldIgnoreAggregateWhenAlreadySeenAllAttestingValidators() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof validAggregate = generator.validAggregateAndProof(chainHead);
    final AttestationData attestationData = validAggregate.getMessage().getAggregate().getData();
    final UInt64 committeeIndex =
        validAggregate.getMessage().getAggregate().getFirstCommitteeIndex();
    final ValidatableAttestation smallAttestation =
        createValidAggregate(ONE, attestationData, committeeIndex, true, false, false);
    final ValidatableAttestation largeAttestation =
        createValidAggregate(
            validAggregate.getMessage().getIndex(),
            attestationData,
            committeeIndex,
            true,
            true,
            true);

    validator.addSeenAggregate(largeAttestation);
    assertThat(validator.validate(smallAttestation))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldAcceptAggregateWhenAlreadySeenAllAttestingValidatorsButOnDifferentCommitteeIndex(
      final SpecContext specContext) {
    // this test applies only to electra
    specContext.assumeElectraActive();
    disableSignatureVerification();

    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof validAggregate = generator.validAggregateAndProof(chainHead);
    final AttestationData attestationData = validAggregate.getMessage().getAggregate().getData();
    final UInt64 committeeIndex =
        validAggregate.getMessage().getAggregate().getFirstCommitteeIndex();

    final ValidatableAttestation smallAttestation =
        createValidAggregate(
            validAggregate.getMessage().getIndex(),
            attestationData,
            committeeIndex,
            true,
            false,
            false);
    final ValidatableAttestation largeAttestation =
        createValidAggregate(
            validAggregate.getMessage().getIndex(),
            attestationData,
            committeeIndex.increment(),
            true,
            true,
            true);

    validator.addSeenAggregate(largeAttestation);
    assertThat(validator.validate(smallAttestation))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldAcceptAggregateWhenNotAllAttestingValidatorsSeen() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final AggregateAndProof validAggregate =
        generator.validAggregateAndProof(chainHead).getMessage();
    final AttestationData attestationData = validAggregate.getAggregate().getData();
    final UInt64 committeeIndex = validAggregate.getAggregate().getFirstCommitteeIndex();
    final ValidatableAttestation smallAttestation =
        createValidAggregate(ONE, attestationData, committeeIndex, true, false, false);
    final ValidatableAttestation largeAttestation =
        createValidAggregate(
            validAggregate.getIndex(), attestationData, committeeIndex, true, true, true);

    validator.addSeenAggregate(smallAttestation);
    assertThat(validator.validate(largeAttestation))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldAcceptAggregateWhenNoSupersetOfValidatorsSeen() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final AggregateAndProof validAggregate =
        generator.validAggregateAndProof(chainHead).getMessage();
    final AttestationData attestationData = validAggregate.getAggregate().getData();
    final UInt64 committeeIndex = validAggregate.getAggregate().getFirstCommitteeIndex();
    final ValidatableAttestation smallAttestation1 =
        createValidAggregate(ONE, attestationData, committeeIndex, true, false, true);
    final ValidatableAttestation smallAttestation2 =
        createValidAggregate(UInt64.valueOf(2), attestationData, committeeIndex, false, true, true);
    final ValidatableAttestation attestation =
        createValidAggregate(
            validAggregate.getIndex(), attestationData, committeeIndex, true, true, false);

    validator.addSeenAggregate(smallAttestation1);
    validator.addSeenAggregate(smallAttestation2);

    // Accept because neither of the small attestations includes both the validators this one does
    assertThat(validator.validate(attestation))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  private ValidatableAttestation createValidAggregate(
      final UInt64 validatorIndex,
      final AttestationData attestationData,
      final UInt64 committeeIndex,
      final Boolean... aggregationBitsPrefix) {
    final BeaconState state = storageSystem.getChainHead().getState();
    final int committeeSize =
        spec.getBeaconCommittee(state, attestationData.getSlot(), attestationData.getIndex())
            .size();
    // fill rest of the aggregation bits with 0b0
    final boolean[] aggregationBits = new boolean[committeeSize];
    IntStream.range(0, Math.min(aggregationBitsPrefix.length, committeeSize))
        .forEach(idx -> aggregationBits[idx] = aggregationBitsPrefix[idx]);
    final SszBitlist sszAggregationBits =
        aggregateAndProofSchema
            .getAttestationSchema()
            .getAggregationBitsSchema()
            .of(ArrayUtils.toObject(aggregationBits));
    final Attestation attestation =
        aggregateAndProofSchema
            .getAttestationSchema()
            .create(
                sszAggregationBits,
                attestationData,
                BLSSignature.empty(),
                getCommitteeBitsSupplier(
                    aggregateAndProofSchema.getAttestationSchema(), committeeIndex));
    final SignedAggregateAndProof signedAggregate =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(validatorIndex, attestation, BLSSignature.empty()),
            BLSSignature.empty());
    whenAttestationIsValid(signedAggregate);
    return ValidatableAttestation.aggregateFromValidator(spec, signedAggregate);
  }

  @TestTemplate
  public void shouldAcceptAggregateWithSameSlotAndDifferentAggregatorIndex(
      final SpecContext specContext) {
    // can run only on phase 0, we should find magic combination for ELECTRA too
    specContext.assumeIsNotOneOf(ELECTRA);

    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final List<Attestation> aggregatesForSlot =
        AttestationGenerator.groupAndAggregateAttestations(
            generator
                .getAttestationGenerator()
                .getAttestationsForSlot(chainHead, chainHead.getSlot()));
    final Attestation aggregate2 =
        aggregatesForSlot.stream()
            .filter(attestation -> !hasSameCommitteeIndex(aggregateAndProof1, attestation))
            .findFirst()
            .orElseThrow();
    final SignedAggregateAndProof aggregateAndProof2 =
        generator.generator().blockAndState(chainHead).aggregate(aggregate2).generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void shouldAcceptAggregateWithSameAggregatorIndexAndDifferentSlot(
      final SpecContext specContext) {
    // can run only on phase 0, we should find magic combination for ELECTRA too
    specContext.assumeIsNotOneOf(ELECTRA);

    chainUpdater.setCurrentSlot(ONE);
    final BeaconBlockAndState chainHead = bestBlock.toUnsigned();

    // We need a validator that is an aggregator for both epoch 0 and 1. 238 happens to be one.
    final UInt64 aggregatorIndex = UInt64.valueOf(238);
    final SignedAggregateAndProof aggregateAndProof1 =
        generator
            .generator()
            .blockAndState(genesis.toUnsigned())
            .aggregatorIndex(aggregatorIndex)
            .generate();

    final CommitteeAssignment epochOneCommitteeAssignment =
        getCommitteeAssignment(chainHead, aggregatorIndex.intValue(), ONE);
    final SignedAggregateAndProof aggregateAndProof2 =
        generator
            .generator()
            .blockAndState(chainHead, epochOneCommitteeAssignment.slot())
            .committeeIndex(epochOneCommitteeAssignment.committeeIndex())
            .aggregatorIndex(aggregatorIndex)
            .generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate().getData().getSlot())
        .isNotEqualTo(aggregateAndProof2.getMessage().getAggregate().getData().getSlot());
    assertThat(aggregateAndProof1.getMessage().getIndex())
        .isEqualTo(aggregateAndProof2.getMessage().getIndex());

    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void shouldRejectAggregateWhenSelectionProofDoesNotSelectAsAggregator() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    int aggregatorIndex = 3;
    final CommitteeAssignment committeeAssignment =
        getCommitteeAssignment(chainHead, aggregatorIndex, ZERO);
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(chainHead, committeeAssignment.slot())
            .aggregatorIndex(UInt64.valueOf(aggregatorIndex))
            .committeeIndex(committeeAssignment.committeeIndex())
            .generate();
    whenAttestationIsValid(aggregate);
    // Sanity check
    final int committeeLength = committeeAssignment.committee().size();
    final int aggregatorModulo =
        genesisSpec.getValidatorsUtil().getAggregatorModulo(committeeLength);
    assertThat(aggregatorModulo).isGreaterThan(1);
    assertThat(
            genesisSpec
                .getValidatorsUtil()
                .isAggregator(aggregate.getMessage().getSelectionProof(), aggregatorModulo))
        .isFalse();

    assertThat(validator.validate(ValidatableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  public void shouldRejectIfAggregatorIndexIsNotWithinTheCommittee() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final int aggregatorIndex = 60;
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(chainHead)
            .aggregatorIndex(UInt64.valueOf(aggregatorIndex))
            .generate();
    whenAttestationIsValid(aggregate);
    // Sanity check aggregator is not in the committee
    final AttestationData attestationData = aggregate.getMessage().getAggregate().getData();
    final CommitteeAssignment committeeAssignment =
        getCommitteeAssignment(
            chainHead, aggregatorIndex, spec.computeEpochAtSlot(chainHead.getSlot()));
    if (committeeAssignment.committeeIndex().equals(attestationData.getIndex())
        && committeeAssignment.slot().equals(attestationData.getSlot())) {
      fail("Aggregator was in the committee");
    }

    assertThat(validator.validate(ValidatableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  public void shouldRejectIfSelectionProofIsNotAValidSignatureOfAggregatorIndex() {
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(storageSystem.getChainHead())
            .aggregatorIndex(ONE)
            .selectionProof(dataStructureUtil.randomSignature())
            .generate();
    whenAttestationIsValid(aggregate);

    assertThat(validator.validate(ValidatableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  public void shouldRejectIfAggregateAndProofSignatureIsNotValid() {
    final SignedAggregateAndProof validAggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final SignedAggregateAndProof invalidAggregate =
        signedAggregateAndProofSchema.create(
            validAggregate.getMessage(), dataStructureUtil.randomSignature());
    whenAttestationIsValid(invalidAggregate);
    whenAttestationIsValid(validAggregate);

    assertThat(
            validator.validate(
                ValidatableAttestation.aggregateFromValidator(spec, invalidAggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
    assertThat(
            validator.validate(ValidatableAttestation.aggregateFromValidator(spec, validAggregate)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  private boolean hasSameCommitteeIndex(
      final SignedAggregateAndProof aggregateAndProof, final Attestation attestation) {
    return attestation
        .getFirstCommitteeIndex()
        .equals(aggregateAndProof.getMessage().getAggregate().getFirstCommitteeIndex());
  }

  private void whenAttestationIsValid(final SignedAggregateAndProof aggregate) {
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    final BeaconState state = getStateFor(aggregate).orElseThrow();
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.accept(state)));
  }

  private void whenAttestationIsSavedForFuture(final SignedAggregateAndProof aggregate) {
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);
    final BeaconState state = getStateFor(aggregate).orElseThrow();
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture(state)));
  }

  private Optional<BeaconState> getStateFor(final SignedAggregateAndProof aggregate) {
    return safeJoin(
        storageSystem
            .recentChainData()
            .retrieveBlockState(
                aggregate.getMessage().getAggregate().getData().getBeaconBlockRoot()));
  }

  private CommitteeAssignment getCommitteeAssignment(
      final StateAndBlockSummary chainHead, final int aggregatorIndex, final UInt64 epoch) {
    return spec.getCommitteeAssignment(chainHead.getState(), epoch, aggregatorIndex).orElseThrow();
  }

  private Supplier<SszBitvector> getCommitteeBitsSupplier(
      final AttestationSchema<?> attestationSchema, final UInt64 committeeIndex) {
    return () ->
        attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndex.intValue());
  }
}
