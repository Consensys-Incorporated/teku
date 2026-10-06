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

package tech.pegasys.teku.spec.logic.versions.altair.block;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.SYNC_REWARD_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_HEAD_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_TARGET_FLAG_INDEX;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public class BlockProcessorAltair extends AbstractBlockProcessor {
  private final SpecConfigAltair specConfigAltair;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;
  private final SyncCommitteeUtil syncCommitteeUtil;

  public static BlockProcessorAltair required(final BlockProcessor blockProcessor) {
    return blockProcessor
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an altair blockProcessor but got: "
                        + blockProcessor.getClass().getSimpleName()));
  }

  public BlockProcessorAltair(
      final SpecConfigAltair specConfig,
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator);

    this.specConfigAltair = specConfig;
    this.miscHelpersAltair = miscHelpers;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
    this.syncCommitteeUtil = syncCommitteeUtil;
  }

  @Override
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
    final BeaconBlockBodyAltair blockBody = BeaconBlockBodyAltair.required(block.getBody());

    super.processBlock(state, block, indexedAttestationCache, signatureVerifier, payloadExecutor);
    processSyncAggregate(state, blockBody.getSyncAggregate(), signatureVerifier);
  }

  @Override
  protected void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider,
      final UInt64 parentSlot) {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
    final AttestationProcessingResult result =
        processAttestation(state, attestation, indexedAttestationProvider, parentSlot);
    consumeAttestationProcessingResult(attestation.getData(), result, genericState);
  }

  public record AttestationProcessingResult(
      Optional<UInt64> proposerReward, int builderPaymentIndex, UInt64 builderPaymentWeightDelta) {}

  public AttestationProcessingResult processAttestation(
      final MutableBeaconStateAltair state,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider) {
    return processAttestation(
        state,
        attestation,
        indexedAttestationProvider,
        beaconStateAccessorsAltair.getAttestationParentSlot(state));
  }

  public AttestationProcessingResult processAttestation(
      final MutableBeaconStateAltair state,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider,
      final UInt64 parentSlot) {
    final AttestationData data = attestation.getData();

    final List<Integer> participationFlagIndices =
        beaconStateAccessorsAltair.getAttestationParticipationFlagIndices(
            state, data, state.getSlot().minus(data.getSlot()), parentSlot);

    // Update epoch participation flags
    final boolean currentEpochTarget =
        data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state));
    final SszMutableList<SszByte> epochParticipation;
    if (currentEpochTarget) {
      epochParticipation = state.getCurrentEpochParticipation();
    } else {
      epochParticipation = state.getPreviousEpochParticipation();
    }
    final int builderPaymentIndex = getBuilderPaymentIndex(currentEpochTarget, data);

    // track the weight for pending builder payment
    UInt64 builderPaymentWeightDelta = UInt64.ZERO;

    UInt64 proposerRewardNumerator = UInt64.ZERO;
    final List<UInt64> attestingIndices =
        indexedAttestationProvider.getIndexedAttestation(attestation).attestingIndices();
    for (final UInt64 attestingIndex : attestingIndices) {
      final int index = attestingIndex.intValue();
      final byte previousParticipationFlags = epochParticipation.get(index).get();
      byte newParticipationFlags = 0;
      final UInt64 baseReward = beaconStateAccessorsAltair.getBaseReward(state, index);
      for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
        final UInt64 weight = PARTICIPATION_FLAG_WEIGHTS.get(flagIndex);

        if (participationFlagIndices.contains(flagIndex)
            && !miscHelpersAltair.hasFlag(previousParticipationFlags, flagIndex)) {
          newParticipationFlags = miscHelpersAltair.addFlag(newParticipationFlags, flagIndex);
          proposerRewardNumerator = proposerRewardNumerator.plus(baseReward.times(weight));
        }
      }

      if (newParticipationFlags != 0) {
        epochParticipation.set(
            index,
            SszByte.of(
                miscHelpersAltair.addFlags(previousParticipationFlags, newParticipationFlags)));

        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .onAttestation(
                state.getValidators().get(index),
                currentEpochTarget,
                miscHelpersAltair.hasFlag(newParticipationFlags, TIMELY_SOURCE_FLAG_INDEX),
                miscHelpersAltair.hasFlag(newParticipationFlags, TIMELY_TARGET_FLAG_INDEX),
                miscHelpersAltair.hasFlag(newParticipationFlags, TIMELY_HEAD_FLAG_INDEX));

        builderPaymentWeightDelta =
            updateBuilderPaymentWeight(
                previousParticipationFlags,
                builderPaymentIndex,
                builderPaymentWeightDelta,
                data,
                index,
                state);
      }
    }

    Optional<UInt64> proposerReward = Optional.empty();

    if (!proposerRewardNumerator.isZero()) {
      final UInt64 proposerRewardDenominator =
          WEIGHT_DENOMINATOR
              .minus(PROPOSER_WEIGHT)
              .times(WEIGHT_DENOMINATOR)
              .dividedBy(PROPOSER_WEIGHT);
      proposerReward = Optional.of(proposerRewardNumerator.dividedBy(proposerRewardDenominator));
    }

    return new AttestationProcessingResult(
        proposerReward, builderPaymentIndex, builderPaymentWeightDelta);
  }

  protected int getBuilderPaymentIndex(
      final boolean currentEpochTarget, final AttestationData data) {
    // NO-OP
    return 0;
  }

  protected UInt64 updateBuilderPaymentWeight(
      final byte previousParticipationFlags,
      final int builderPaymentIndex,
      final UInt64 builderPaymentWeightDelta,
      final AttestationData data,
      final int attestingIndex,
      final BeaconState state) {
    // NO-OP
    return builderPaymentWeightDelta;
  }

  protected void consumeAttestationProcessingResult(
      final AttestationData data,
      final AttestationProcessingResult result,
      final MutableBeaconState state) {
    result.proposerReward.ifPresent(
        delta -> {
          final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state);
          beaconStateMutators.increaseProposerBalance(state, proposerIndex, delta);
        });
  }

  @Override
  public void processSyncAggregate(
      final MutableBeaconState baseState,
      final SyncAggregate aggregate,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(baseState);
    final List<BLSPublicKey> participantPubkeys = new ArrayList<>();

    // Compute participant and proposer rewards
    final UInt64 participantReward = computeParticipantReward(state);
    final UInt64 proposerReward =
        participantReward
            .times(PROPOSER_WEIGHT)
            .dividedBy(WEIGHT_DENOMINATOR.minus(PROPOSER_WEIGHT));

    final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state);

    for (int i = 0; i < specConfigAltair.getSyncCommitteeSize(); i++) {
      final int validatorIndex =
          syncCommitteeUtil.getCurrentSyncCommitteeParticipantValidatorIndex(state, i);
      final BLSPublicKey publicKey =
          beaconStateAccessors
              .getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
              .orElseThrow(
                  () -> new IllegalStateException("Validator in sync committee has no public key"));
      if (aggregate.getSyncCommitteeBits().getBit(i)) {
        participantPubkeys.add(publicKey);
        beaconStateMutators.increaseBalance(state, validatorIndex, participantReward);
        beaconStateMutators.increaseProposerBalance(state, proposerIndex, proposerReward);
      } else {
        beaconStateMutators.decreaseBalance(state, validatorIndex, participantReward);
      }
    }

    final UInt64 previousSlot = state.getSlot().minusMinZero(1);
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.SYNC_COMMITTEE,
            miscHelpers.computeEpochAtSlot(previousSlot));
    final Bytes32 signingRoot =
        miscHelpersAltair.computeSigningRoot(
            beaconStateAccessors.getBlockRootAtSlot(state, previousSlot), domain);

    if (!eth2FastAggregateVerify(
        signatureVerifier,
        participantPubkeys,
        signingRoot,
        aggregate.getSyncCommitteeSignature().getSignature())) {
      throw new BlockProcessingException("Invalid sync committee signature in " + aggregate);
    }
  }

  @Override
  public UInt64 computeParticipantReward(final BeaconStateAltair state) {
    final UInt64 totalActiveIncrements =
        beaconStateAccessors
            .getTotalActiveBalance(state)
            .dividedBy(specConfig.getEffectiveBalanceIncrement());
    final UInt64 totalBaseRewards =
        beaconStateAccessorsAltair.getBaseRewardPerIncrement(state).times(totalActiveIncrements);
    final UInt64 maxParticipantRewards =
        totalBaseRewards
            .times(SYNC_REWARD_WEIGHT)
            .dividedBy(WEIGHT_DENOMINATOR)
            .dividedBy(specConfig.getSlotsPerEpoch());
    return maxParticipantRewards.dividedBy(specConfigAltair.getSyncCommitteeSize());
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayload in Altair");
  }

  @Override
  public void validateExecutionPayload(
      final BeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayload in Altair");
  }

  @Override
  public void validateExecutionPayloadHeader(
      final BeaconState state, final ExecutionPayloadHeader executionPayloadHeader)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayloadHeader in Altair");
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No NewPayloadRequest in Altair");
  }

  @Override
  public boolean isOptimistic() {
    return false;
  }

  @Override
  public void processBlsToExecutionChanges(
      final MutableBeaconState state,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No BlsToExecutionChanges in Altair.");
  }

  @Override
  public void processParentExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlock beaconBlock,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    // NO-OP until Gloas
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No withdrawals in Altair");
  }

  @Override
  public void processExecutionPayloadBid(
      final MutableBeaconState state, final SignedExecutionPayloadBid signedBid)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No process_execution_payload_bid until Gloas");
  }

  @Override
  public void processPayloadAttestations(
      final MutableBeaconState state, final SszList<PayloadAttestation> payloadAttestations)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No payload attestations until Gloas");
  }

  public static boolean eth2FastAggregateVerify(
      final BLSSignatureVerifier signatureVerifier,
      final List<BLSPublicKey> pubkeys,
      final Bytes32 message,
      final BLSSignature signature) {
    // BLS verify logic would throw if we pass in an empty list of public keys,
    // so if the keys list is empty, return the isInfinity of the signature.
    // this is equivalent to the spec, and removes the possibility of an empty list
    // causing an exception in the signature verifier.
    if (pubkeys.isEmpty()) {
      return signature.isInfinity();
    }
    return signatureVerifier.verify(pubkeys, message, signature);
  }

  @Override
  public Optional<BlockProcessorAltair> toVersionAltair() {
    return Optional.of(this);
  }
}
