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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.fulu.block.BlockProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BlockProcessorGloas extends BlockProcessorFulu {

  private static final Logger LOG = LogManager.getLogger();

  private final PredicatesGloas predicatesGloas;
  private final SpecConfigGloas specConfigGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;
  private final MiscHelpersGloas miscHelpersGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;
  private final BeaconStateMutatorsGloas beaconStateMutatorsGloas;
  private final AttestationUtilGloas attestationUtilGloas;
  private final ExecutionRequestsProcessorGloas executionRequestsProcessorGloas;

  public BlockProcessorGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilGloas attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsGloas schemaDefinitions,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorGloas executionRequestsProcessor) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        schemaDefinitions,
        withdrawalsHelpers,
        executionRequestsDataCodec,
        executionRequestsProcessor);
    this.predicatesGloas = predicates;
    this.specConfigGloas = specConfig;
    this.schemaDefinitionsGloas = schemaDefinitions;
    this.miscHelpersGloas = miscHelpers;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.attestationUtilGloas = attestationUtil;
    this.executionRequestsProcessorGloas = executionRequestsProcessor;
  }

  @Override
  public UInt64 executionProcessing(
      final MutableBeaconState genericState,
      final BeaconBlock beaconBlock,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlotFallback)
      throws BlockProcessingException {
    safelyProcess(
        () ->
            processParentExecutionPayload(genericState, beaconBlock, validatorExitContextSupplier));
    processWithdrawals(genericState, Optional.empty());
    return safelyProcessAndReturn(
        () ->
            processExecutionPayloadBid(
                genericState,
                BeaconBlockBodyGloas.required(beaconBlock.getBody())
                    .getSignedExecutionPayloadBid()));
  }

  // process_parent_execution_payload
  @Override
  public void processParentExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlock beaconBlock,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(beaconBlock.getBody());
    final ExecutionPayloadBid bid = body.getSignedExecutionPayloadBid().getMessage();
    final ExecutionRequests requests = body.getParentExecutionRequests();

    if (!miscHelpersGloas.isBidBuildingOnFullParent(stateGloas, bid)) {
      // Parent was EMPTY -- no execution requests expected
      if (!requests.isDefault()) {
        throw new BlockProcessingException(
            "No execution requests were expected for an EMPTY parent");
      }
      return;
    }

    // Parent was FULL -- verify the bid commitment and apply the payload
    if (!miscHelpersGloas.isExecutionRequestsRootMatchingLatestBid(stateGloas, requests)) {
      throw new BlockProcessingException(
          "The execution requests root in the latest committed bid does not match the parent execution requests in the block");
    }
    safelyProcess(
        () -> applyParentExecutionPayload(stateGloas, requests, validatorExitContextSupplier));
  }

  // apply_parent_execution_payload
  public void applyParentExecutionPayload(
      final MutableBeaconStateGloas state,
      final ExecutionRequests requests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final ExecutionRequestsGloas requestsGloas = ExecutionRequestsGloas.required(requests);
    checkArgument(
        requests.getWithdrawals().size() <= specConfigGloas.getMaxWithdrawalRequestsPerPayload(),
        "Too many withdrawal requests");
    checkArgument(
        requests.getConsolidations().size()
            <= specConfigGloas.getMaxConsolidationRequestsPerPayload(),
        "Too many consolidation requests");
    checkArgument(
        requestsGloas.getBuilderDeposits().size()
            <= specConfigGloas.getMaxBuilderDepositRequestsPerPayload(),
        "Too many builder deposit requests");
    checkArgument(
        requestsGloas.getBuilderExits().size()
            <= specConfigGloas.getMaxBuilderExitRequestsPerPayload(),
        "Too many builder exit requests");

    final ExecutionPayloadBid parentBid = state.getLatestExecutionPayloadBid();
    final UInt64 parentSlot = parentBid.getSlot();
    final UInt64 parentEpoch = miscHelpers.computeEpochAtSlot(parentSlot);

    // Process execution requests from parent's payload. The execution requests are processed at
    // state.slot (child's slot), not the parent's slot.
    final long startTimeMillis = System.currentTimeMillis();
    LOG.debug("Starting processing {} deposit requests", requests.getDeposits().size());
    executionRequestsProcessorGloas.processDepositRequests(state, requests.getDeposits());
    LOG.debug(
        "Finished processing {} deposit requests. Pending deposits: {}, builders: {}. Took {} ms.",
        requests.getDeposits().size(),
        state.getPendingDeposits().size(),
        state.getBuilders().size(),
        System.currentTimeMillis() - startTimeMillis);
    executionRequestsProcessorGloas.processWithdrawalRequests(
        state, requests.getWithdrawals(), validatorExitContextSupplier);
    executionRequestsProcessorGloas.processConsolidationRequests(
        state, requests.getConsolidations());
    executionRequestsProcessorGloas.processBuilderDepositRequests(
        state, requestsGloas.getBuilderDeposits());
    executionRequestsProcessorGloas.processBuilderExitRequests(
        state, requestsGloas.getBuilderExits());

    // Settle the builder payment
    if (parentEpoch.equals(beaconStateAccessorsGloas.getCurrentEpoch(state))) {
      final UInt64 paymentIndex =
          parentSlot.mod(specConfig.getSlotsPerEpoch()).plus(specConfig.getSlotsPerEpoch());
      beaconStateMutatorsGloas.settleBuilderPayment(state, paymentIndex);
    } else if (parentEpoch.equals(beaconStateAccessorsGloas.getPreviousEpoch(state))) {
      final UInt64 paymentIndex = parentSlot.mod(specConfig.getSlotsPerEpoch());
      beaconStateMutatorsGloas.settleBuilderPayment(state, paymentIndex);
    } else if (parentBid.getValue().isGreaterThan(UInt64.ZERO)) {
      // Parent is older than the previous epoch, its payment entry has been
      // evicted from builder_pending_payments. Append the withdrawal directly.
      state
          .getBuilderPendingWithdrawals()
          .append(
              schemaDefinitionsGloas
                  .getBuilderPendingWithdrawalSchema()
                  .create(
                      parentBid.getFeeRecipient(),
                      parentBid.getValue(),
                      parentBid.getBuilderIndex()));
    }

    // Update parent payload availability and latest block hash
    state.setExecutionPayloadAvailability(
        state
            .getExecutionPayloadAvailability()
            .withBit(parentSlot.mod(specConfig.getSlotsPerHistoricalRoot()).intValue()));
    state.setLatestBlockHash(parentBid.getBlockHash());
  }

  // process_withdrawals with only state as a parameter
  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    safelyProcess(() -> withdrawalsHelpers.processWithdrawals(state));
  }

  // process_execution_payload_bid
  @Override
  public UInt64 processExecutionPayloadBid(
      final MutableBeaconState state, final SignedExecutionPayloadBid signedBid)
      throws BlockProcessingException {

    final ExecutionPayloadBid bid = signedBid.getMessage();

    final UInt64 builderIndex = bid.getBuilderIndex();

    final UInt64 amount = bid.getValue();

    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);

    if (builderIndex.equals(BUILDER_INDEX_SELF_BUILD)) {
      // For self-builds, amount must be zero regardless of withdrawal credential prefix
      if (!amount.isZero()) {
        throw new BlockProcessingException("Amount must be zero for self-build blocks");
      }
      if (!signedBid.getSignature().isInfinity()) {
        throw new BlockProcessingException(
            "Signature must be G2_POINT_AT_INFINITY for self-builds");
      }
    } else {
      // Verify that the builder is active
      if (!predicatesGloas.isActiveBuilder(state, builderIndex)) {
        throw new BlockProcessingException("Builder is not active");
      }
      // Verify that the builder is a payload builder
      if (stateGloas.getBuilders().get(builderIndex.intValue()).getVersion()
          != PAYLOAD_BUILDER_VERSION) {
        throw new BlockProcessingException("Builder is not a payload builder");
      }
      // Verify that the builder has funds to cover the bid
      if (!beaconStateAccessorsGloas.canBuilderCoverBid(state, builderIndex, amount)) {
        throw new BlockProcessingException("Builder doesn't have funds to cover the bid");
      }
      if (!operationSignatureVerifier.verifyExecutionPayloadBidSignature(
          state, signedBid, BLSSignatureVerifier.SIMPLE)) {
        throw new BlockProcessingException("Signature for the signed bind was invalid");
      }
    }

    // Verify commitments are under limit
    if (bid.getBlobKzgCommitments().size()
        > miscHelpersGloas
            .getBlobParameters(beaconStateAccessors.getCurrentEpoch(state))
            .maxBlobsPerBlock()) {
      throw new BlockProcessingException(
          "Number of kzg commitments in the bid exceeds max blobs per block");
    }

    // Verify that the bid is for the current slot
    if (!bid.getSlot().equals(state.getSlot())) {
      throw new BlockProcessingException("Bid is not for the current slot");
    }

    // Verify that the bid is for the right parent block
    if (!bid.getParentBlockHash().equals(stateGloas.getLatestBlockHash())
        || !bid.getParentBlockRoot()
            .equals(
                beaconStateAccessors.getBlockRootAtSlot(state, state.getSlot().minusMinZero(1)))) {
      throw new BlockProcessingException("Bid is not for the right parent block");
    }

    // Verify that the bid's block hash differs from its parent block hash
    if (bid.getBlockHash().equals(bid.getParentBlockHash())) {
      throw new BlockProcessingException("Bid's block hash is the same as the parent block hash");
    }

    if (!bid.getPrevRandao()
        .equals(
            beaconStateAccessors.getRandaoMix(
                state, beaconStateAccessors.getCurrentEpoch(state)))) {
      throw new BlockProcessingException("Prev randao of the bid is not as expected");
    }

    // Record the pending payment if there is some payment
    if (amount.isGreaterThan(UInt64.ZERO)) {
      final BuilderPendingPayment pendingPayment =
          schemaDefinitionsGloas
              .getBuilderPendingPaymentSchema()
              .create(
                  UInt64.ZERO,
                  schemaDefinitionsGloas
                      .getBuilderPendingWithdrawalSchema()
                      .create(bid.getFeeRecipient(), amount, builderIndex),
                  UInt64.valueOf(beaconStateAccessors.getBeaconProposerIndex(state)));

      stateGloas
          .getBuilderPendingPayments()
          .set(
              bid.getSlot()
                  .mod(specConfig.getSlotsPerEpoch())
                  .plus(specConfig.getSlotsPerEpoch())
                  .intValue(),
              pendingPayment);
    }

    // Cache the execution payload bid
    final UInt64 parentSlot = stateGloas.getLatestExecutionPayloadBid().getSlot();
    stateGloas.setLatestExecutionPayloadBid(bid);
    return parentSlot;
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor) {
    throw new UnsupportedOperationException("process_execution_payload has been removed in Gloas");
  }

  // Remove the BuilderPendingPayment corresponding to this proposal if it is still in the 2-epoch
  // window. Only clear it when the slashed validator is the proposer associated with the payment;
  // otherwise an unrelated same-slot equivocation could grief an honest proposer's payment.
  @Override
  protected void removeBuilderPendingPayment(
      final ProposerSlashing proposerSlashing, final MutableBeaconState state) {
    final UInt64 slot = proposerSlashing.getHeader1().getMessage().getSlot();
    final UInt64 proposerIndex = proposerSlashing.getHeader1().getMessage().getProposerIndex();
    final UInt64 proposalEpoch = miscHelpers.computeEpochAtSlot(slot);
    OptionalInt paymentIndex = OptionalInt.empty();
    if (proposalEpoch.equals(beaconStateAccessors.getCurrentEpoch(state))) {
      paymentIndex =
          OptionalInt.of(
              specConfig.getSlotsPerEpoch() + slot.mod(specConfig.getSlotsPerEpoch()).intValue());
    } else if (proposalEpoch.equals(beaconStateAccessors.getPreviousEpoch(state))) {
      paymentIndex = OptionalInt.of(slot.mod(specConfig.getSlotsPerEpoch()).intValue());
    }
    paymentIndex.ifPresent(
        index -> {
          final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
          final BuilderPendingPayment payment = stateGloas.getBuilderPendingPayments().get(index);
          if (payment.getProposerIndex().equals(proposerIndex)) {
            stateGloas
                .getBuilderPendingPayments()
                .set(index, schemaDefinitionsGloas.getBuilderPendingPaymentSchema().getDefault());
          }
        });
  }

  @Override
  protected int getBuilderPaymentIndex(
      final boolean currentEpochTarget, final AttestationData data) {
    if (currentEpochTarget) {
      return specConfig.getSlotsPerEpoch()
          + data.getSlot().mod(specConfig.getSlotsPerEpoch()).intValue();
    } else {
      return data.getSlot().mod(specConfig.getSlotsPerEpoch()).intValue();
    }
  }

  @Override
  protected UInt64 updateBuilderPaymentWeight(
      final byte previousParticipationFlags,
      final int builderPaymentIndex,
      final UInt64 builderPaymentWeightDelta,
      final AttestationData data,
      final int attestingIndex,
      final BeaconState state) {
    final BuilderPendingPayment payment =
        BeaconStateGloas.required(state).getBuilderPendingPayments().get(builderPaymentIndex);
    if (previousParticipationFlags == ParticipationFlags.NO_PARTICIPATION_FLAGS
        && beaconStateAccessorsGloas.isAttestationSameSlot(state, data)
        // only add to the payment quorum if the payment is not trivial
        && payment.getWithdrawal().getAmount().isGreaterThan(UInt64.ZERO)) {
      return builderPaymentWeightDelta.plus(
          state.getValidators().get(attestingIndex).getEffectiveBalance());
    } else {
      return builderPaymentWeightDelta;
    }
  }

  @Override
  protected void consumeAttestationProcessingResult(
      final AttestationData data,
      final AttestationProcessingResult result,
      final MutableBeaconState state) {
    super.consumeAttestationProcessingResult(data, result, state);
    // update builder payment weight
    final UInt64 weightDelta = result.builderPaymentWeightDelta();
    if (!weightDelta.isZero()) {
      final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
      final BuilderPendingPayment payment =
          stateGloas.getBuilderPendingPayments().get(result.builderPaymentIndex());
      stateGloas
          .getBuilderPendingPayments()
          .set(
              result.builderPaymentIndex(),
              payment.copyWithNewWeight(payment.getWeight().plus(weightDelta)));
    }
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    final BeaconBlockBodyGloas bodyGloas = BeaconBlockBodyGloas.required(body);
    safelyProcess(
        () -> {
          checkArgument(
              body.getProposerSlashings().size() <= specConfigGloas.getMaxProposerSlashings(),
              "Too many proposer slashings");
          checkArgument(
              body.getAttesterSlashings().size()
                  <= specConfigGloas.getMaxAttesterSlashingsElectra(),
              "Too many attester slashings");
          checkArgument(
              body.getAttestations().size() <= specConfigGloas.getMaxAttestationsElectra(),
              "Too many attestations");
          checkArgument(
              body.getVoluntaryExits().size() <= specConfigGloas.getMaxVoluntaryExits(),
              "Too many voluntary exits");
          checkArgument(
              bodyGloas.getBlsToExecutionChanges().size()
                  <= specConfigGloas.getMaxBlsToExecutionChanges(),
              "Too many BLS to execution changes");
          checkArgument(
              bodyGloas.getPayloadAttestations().size()
                  <= specConfigGloas.getMaxPayloadAttestations(),
              "Too many payload attestations");
        });

    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier, parentSlot);

    safelyProcess(
        () ->
            processPayloadAttestations(
                state,
                body.getOptionalPayloadAttestations()
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "Payload attestations expected as part of the body"))));
  }

  @Override
  public void processExecutionRequests(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    // Execution requests are removed from the BeaconBlockBody in Gloas and are instead processed as
    // part of process_execution_payload
  }

  @Override
  public void processPayloadAttestations(
      final MutableBeaconState state, final SszList<PayloadAttestation> payloadAttestations)
      throws BlockProcessingException {
    // process_payload_attestation
    for (final PayloadAttestation payloadAttestation : payloadAttestations) {
      final PayloadAttestationData data = payloadAttestation.getData();
      // Check that the attestation is for the parent beacon block
      if (!data.getBeaconBlockRoot().equals(state.getLatestBlockHeader().getParentRoot())) {
        throw new BlockProcessingException("Attestation is NOT for the parent beacon block");
      }
      // Check that the attestation is for the previous slot
      if (!data.getSlot().increment().equals(state.getSlot())) {
        throw new BlockProcessingException("Attestation is NOT for the previous slot");
      }
      // Verify signature
      final IndexedPayloadAttestationLight indexedPayloadAttestation =
          beaconStateAccessorsGloas.getIndexedPayloadAttestation(state, payloadAttestation);

      if (!attestationUtilGloas.isValidIndexedPayloadAttestation(
          state, indexedPayloadAttestation)) {
        throw new BlockProcessingException("Indexed payload attestation is NOT valid");
      }
    }
  }
}
