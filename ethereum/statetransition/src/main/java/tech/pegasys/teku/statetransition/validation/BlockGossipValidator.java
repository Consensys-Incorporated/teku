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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_ALREADY_SEEN;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_EQUIVOCATION_DETECTED;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.storage.client.ProposerEquivocationTracker;

public class BlockGossipValidator {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_EQUIVOCATION_SIGNATURE_ATTEMPTS = 2;
  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;
  private final ProposerEquivocationTracker proposerEquivocationTracker;
  private final SigningRootUtil signingRootUtil;
  private final Map<SlotAndProposer, Bytes32> receivedValidBlockRoots =
      LimitedMap.createNonSynchronized(VALID_BLOCK_SET_SIZE);
  private final Map<SlotAndProposer, Set<Bytes32>> attemptedEquivocationRoots =
      LimitedMap.createNonSynchronized(VALID_BLOCK_SET_SIZE);

  public BlockGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher,
      final ProposerEquivocationTracker proposerEquivocationTracker) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.receivedBlockEventsChannelPublisher = receivedBlockEventsChannelPublisher;
    this.proposerEquivocationTracker = proposerEquivocationTracker;
    signingRootUtil = new SigningRootUtil(spec);
  }

  /**
   * Perform gossip validation on a block.
   *
   * @param block the block to validate
   * @param markAsReceived whether to mark the block as received if it is valid (required for the
   *     equivocation check)
   */
  public SafeFuture<InternalValidationResult> validate(
      final SignedBeaconBlock block, final boolean markAsReceived) {
    return internalValidate(block, markAsReceived)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                receivedBlockEventsChannelPublisher.onBlockValidated(block);
              }
            });
  }

  private SafeFuture<InternalValidationResult> internalValidate(
      final SignedBeaconBlock block, final boolean markAsReceived) {

    /*
     * [IGNORE] The block is from a slot greater than the latest finalized slot -- i.e. validate that
     * signed_beacon_block.message.slot > compute_start_slot_at_epoch(store.finalized_checkpoint.epoch)
     * (a client MAY choose to validate and store such blocks for additional purposes
     * -- e.g. slashing detection, archive nodes, etc.).
     */
    if (gossipValidationHelper.isSlotFinalized(block.getSlot())) {
      LOG.trace("BlockValidator: Block slot {} is finalized. Dropping.", block.getSlot());
      return completedFuture(InternalValidationResult.IGNORE);
    }

    /*
     * [IGNORE] The block is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance)
     * -- i.e. validate that signed_beacon_block.message.slot <= current_slot
     * (a client MAY queue future blocks for processing at the appropriate slot).
     */
    if (gossipValidationHelper.isSlotFromFuture(block.getSlot())) {
      LOG.trace("BlockValidator: Block is from the future. Saving for future processing.");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [New in Gloas:EIP7688]
     * [REJECT] The block body operation counts are within their limits.
     */
    final Optional<InternalValidationResult> operationLimitsValidationResult =
        verifyBlockBodyOperationLimits(block);
    if (operationLimitsValidationResult.isPresent()) {
      return completedFuture(operationLimitsValidationResult.get());
    }

    /*
     * [New in Gloas:EIP7688]
     * [REJECT] The parent execution request counts are within their limits.
     */
    final Optional<InternalValidationResult> executionRequestsLimitsValidationResult =
        verifyExecutionRequestsLimits(block);
    if (executionRequestsLimitsValidationResult.isPresent()) {
      return completedFuture(executionRequestsLimitsValidationResult.get());
    }

    if (gossipValidationHelper.isBlockAvailable(block.getRoot())) {
      LOG.trace("Block is already imported.");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    /*
     * [REJECT] The block's parent (defined by block.parent_root) passes validation.
     */

    /*
     * Saving for future instead of rejecting because the parent block root was not seen but that
     * doesn't mean it's invalid. If the parent block was invalid the current block would already have
     * been rejected in BlockManager#validateAndImportBlock
     */
    if (!gossipValidationHelper.isBlockAvailable(block.getParentRoot())) {
      LOG.trace("Block parent is not available. Saving for future processing.");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of block
     * -- i.e. get_checkpoint_block(store, block.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        block.getSlot(), block.getParentRoot())) {
      return completedFuture(reject("Block does not descend from finalized checkpoint"));
    }

    /*
     * [IGNORE] The block's parent (defined by block.parent_root) has been seen (via gossip or non-gossip sources)
     * (a client MAY queue blocks for processing once the parent block is retrieved).
     */
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "BlockValidator: Parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [REJECT] The block is from a higher slot than its parent.
     */
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();
    if (parentBlockSlot.isGreaterThanOrEqualTo(block.getSlot())) {
      return completedFuture(reject("Parent block is after child block."));
    }

    /*
     * [REJECT] The length of KZG commitments is less than or equal to the limitation defined in Consensus Layer
     */
    final Optional<SszList<SszKZGCommitment>> blobKzgCommitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments();
    if (blobKzgCommitments.isPresent()) {
      final Integer maxBlobsPerBlock =
          spec.getMaxBlobsPerBlockAtSlot(block.getSlot()).orElseThrow();
      final int blobKzgCommitmentsCount = blobKzgCommitments.get().size();
      if (blobKzgCommitmentsCount > maxBlobsPerBlock) {
        LOG.trace(
            "BlockValidator: Block has {} kzg commitments, max allowed {}",
            blobKzgCommitmentsCount,
            maxBlobsPerBlock);
        return completedFuture(
            reject(
                "Block has %d kzg commitments, max allowed %d",
                blobKzgCommitmentsCount, maxBlobsPerBlock));
      }
    }
    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, block.getParentRoot(), block.getSlot())
        .thenApply(
            maybeParentState -> performStatefulValidation(block, maybeParentState, markAsReceived));
  }

  private InternalValidationResult performStatefulValidation(
      final SignedBeaconBlock block,
      final Optional<BeaconState> maybeParentState,
      final boolean markAsReceived) {

    if (maybeParentState.isEmpty()) {
      LOG.trace("Block was available but state wasn't. Must have been pruned by finalized.");
      return InternalValidationResult.IGNORE;
    }
    final BeaconState parentState = maybeParentState.get();
    /*
     * [REJECT] The block is proposed by the expected proposer_index for the block's slot in the context
     * of the current shuffling (defined by parent_root/slot).
     * If the proposer_index cannot immediately be verified against the expected shuffling,
     * the block MAY be queued for later processing while proposers for the block's branch are calculated
     * -- in such a case do not REJECT, instead IGNORE this message.
     */
    if (!gossipValidationHelper.isProposerTheExpectedProposer(
        block.getProposerIndex(), block.getSlot(), parentState)) {
      return reject("Block proposed by incorrect proposer (%s)", block.getProposerIndex());
    }

    final MiscHelpers miscHelpers = spec.atSlot(block.getSlot()).miscHelpers();
    final Optional<SignedExecutionPayloadBid> maybeSignedExecutionPayloadBid =
        block.getMessage().getBody().getOptionalSignedExecutionPayloadBid();
    if (miscHelpers.isMergeTransitionComplete(parentState)
        && maybeSignedExecutionPayloadBid.isEmpty()) {
      final Optional<ExecutionPayload> executionPayload =
          block.getMessage().getBody().getOptionalExecutionPayload();
      if (executionPayload.isEmpty()) {
        return reject("Missing execution payload");
      }
      /*
       * [REJECT] The block's execution payload timestamp is correct with respect to the slot
       * -- i.e. execution_payload.timestamp == compute_time_at_slot(state, block.slot)
       */
      if (executionPayload
              .get()
              .getTimestamp()
              .compareTo(spec.computeTimeAtSlot(parentState, block.getSlot()))
          != 0) {
        return reject("Execution Payload timestamp is not consistent with block slot time");
      }
    }

    if (maybeSignedExecutionPayloadBid.isPresent()) {
      final ExecutionPayloadBid executionPayloadBid =
          maybeSignedExecutionPayloadBid.get().getMessage();
      /*
       * [REJECT] The bid's parent (defined by bid.parent_block_root) equals the block's parent (defined by block.parent_root)
       */
      if (!executionPayloadBid.getParentBlockRoot().equals(block.getParentRoot())) {
        return reject(
            "Execution payload bid has invalid parent block root %s, expecting %s",
            executionPayloadBid.getParentBlockRoot(), block.getParentRoot());
      }

      /*
       * [REJECT] The block's execution payload parent (defined by bid.parent_block_hash) passes
       * validation.
       * Gloas distinguishes EMPTY parents from FULL parents whose payload may arrive
       * later, so this may also SAVE_FOR_FUTURE while waiting for the parent execution payload.
       */
      final Optional<InternalValidationResult> maybeParentExecutionPayloadValidationResult =
          validateExecutionPayloadBidParent(
              block, BeaconStateGloas.required(parentState), executionPayloadBid);
      if (maybeParentExecutionPayloadValidationResult.isPresent()) {
        return maybeParentExecutionPayloadValidationResult.get();
      }
    }

    final EquivocationCheckResult preliminaryEquivocationCheckResult =
        performBlockEquivocationCheck(false, block);
    if (preliminaryEquivocationCheckResult == BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER) {
      return equivocationCheckResultToInternalValidationResult(preliminaryEquivocationCheckResult);
    }
    if (preliminaryEquivocationCheckResult == EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER
        && (!markAsReceived || !reserveEquivocationSignatureAttempt(block))) {
      return equivocationCheckResultToInternalValidationResult(preliminaryEquivocationCheckResult);
    }

    /*
     * [REJECT] The proposer signature, signed_beacon_block.signature, is valid with respect to the proposer_index pubkey.
     */
    if (!blockSignatureIsValidWithRespectToProposerIndex(block, parentState)) {
      return reject("Block signature is invalid");
    }
    if (markAsReceived) {
      proposerEquivocationTracker.onBlockValidated(block);
    }
    final EquivocationCheckResult secondEquivocationCheckResult =
        performBlockEquivocationCheck(markAsReceived, block);

    return equivocationCheckResultToInternalValidationResult(secondEquivocationCheckResult);
  }

  private InternalValidationResult equivocationCheckResultToInternalValidationResult(
      final EquivocationCheckResult equivocationCheckResult) {
    /*
     * [IGNORE] The block is the first block with valid signature received for the proposer for the slot, signed_beacon_block.message.slot
     */
    return switch (equivocationCheckResult) {
      case FIRST_BLOCK_FOR_SLOT_PROPOSER -> InternalValidationResult.ACCEPT;
      case EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER ->
          ignore(IGNORE_EQUIVOCATION_DETECTED, "Equivocating block detected. It will be dropped.");
      case BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER ->
          ignore(
              IGNORE_ALREADY_SEEN,
              "Block is not the first with valid signature for its slot. It will be dropped.");
    };
  }

  /**
   * Verifies that each Gloas block body operation count is within its limit and that the block
   * contains no deposits. This rule is Gloas-only: EIP-7688 turned these operation lists into
   * unbounded progressive lists, so SSZ no longer enforces the limits and this must be checked
   * during gossip validation instead. Pre-Gloas blocks are unaffected, since their operation lists
   * are still bounded at the SSZ level.
   */
  private Optional<InternalValidationResult> verifyBlockBodyOperationLimits(
      final SignedBeaconBlock block) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }

    final BeaconBlockBody body = block.getMessage().getBody();
    final SszList<PayloadAttestation> payloadAttestations =
        body.getOptionalPayloadAttestations().orElseThrow();

    final SpecConfig specConfig = spec.atSlot(block.getSlot()).getConfig();

    final int maxProposerSlashings = specConfig.getMaxProposerSlashings();
    final int proposerSlashingsCount = body.getProposerSlashings().size();
    if (proposerSlashingsCount > maxProposerSlashings) {
      return Optional.of(
          reject(
              "Block has %d proposer slashings, max allowed %d",
              proposerSlashingsCount, maxProposerSlashings));
    }

    final int maxAttesterSlashings =
        SpecConfigElectra.required(specConfig).getMaxAttesterSlashingsElectra();
    final int attesterSlashingsCount = body.getAttesterSlashings().size();
    if (attesterSlashingsCount > maxAttesterSlashings) {
      return Optional.of(
          reject(
              "Block has %d attester slashings, max allowed %d",
              attesterSlashingsCount, maxAttesterSlashings));
    }

    final int maxAttestations = SpecConfigElectra.required(specConfig).getMaxAttestationsElectra();
    final int attestationsCount = body.getAttestations().size();
    if (attestationsCount > maxAttestations) {
      return Optional.of(
          reject("Block has %d attestations, max allowed %d", attestationsCount, maxAttestations));
    }

    final int depositsCount = body.getDeposits().size();
    if (depositsCount != 0) {
      return Optional.of(reject("Block must not contain deposits, found %d", depositsCount));
    }

    final int maxVoluntaryExits = specConfig.getMaxVoluntaryExits();
    final int voluntaryExitsCount = body.getVoluntaryExits().size();
    if (voluntaryExitsCount > maxVoluntaryExits) {
      return Optional.of(
          reject(
              "Block has %d voluntary exits, max allowed %d",
              voluntaryExitsCount, maxVoluntaryExits));
    }

    final int maxBlsToExecutionChanges =
        SpecConfigCapella.required(specConfig).getMaxBlsToExecutionChanges();
    final int blsToExecutionChangesCount =
        body.getOptionalBlsToExecutionChanges().orElseThrow().size();
    if (blsToExecutionChangesCount > maxBlsToExecutionChanges) {
      return Optional.of(
          reject(
              "Block has %d bls to execution changes, max allowed %d",
              blsToExecutionChangesCount, maxBlsToExecutionChanges));
    }

    final int maxPayloadAttestations =
        SpecConfigGloas.required(specConfig).getMaxPayloadAttestations();
    final int payloadAttestationsCount = payloadAttestations.size();
    if (payloadAttestationsCount > maxPayloadAttestations) {
      return Optional.of(
          reject(
              "Block has %d payload attestations, max allowed %d",
              payloadAttestationsCount, maxPayloadAttestations));
    }

    return Optional.empty();
  }

  /**
   * Verifies that each Gloas parent execution request count is within its limit. This rule is
   * Gloas-only: EIP-7688 turned these request lists into unbounded progressive lists, so SSZ no
   * longer enforces the limits and this must be checked during gossip validation instead. Pre-Gloas
   * blocks are unaffected, since they don't carry parent execution requests at all.
   */
  private Optional<InternalValidationResult> verifyExecutionRequestsLimits(
      final SignedBeaconBlock block) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }

    final BeaconBlockBody body = block.getMessage().getBody();
    final ExecutionRequests parentExecutionRequests =
        body.getOptionalParentExecutionRequests().orElseThrow();
    final ExecutionRequestsGloas parentExecutionRequestsGloas =
        ExecutionRequestsGloas.required(parentExecutionRequests);

    final SpecConfig specConfig = spec.atSlot(block.getSlot()).getConfig();

    final int maxWithdrawalRequests =
        SpecConfigElectra.required(specConfig).getMaxWithdrawalRequestsPerPayload();
    final int withdrawalRequestsCount = parentExecutionRequests.getWithdrawals().size();
    if (withdrawalRequestsCount > maxWithdrawalRequests) {
      return Optional.of(
          reject(
              "Parent execution requests has %d withdrawal requests, max allowed %d",
              withdrawalRequestsCount, maxWithdrawalRequests));
    }

    final int maxConsolidationRequests =
        SpecConfigElectra.required(specConfig).getMaxConsolidationRequestsPerPayload();
    final int consolidationRequestsCount = parentExecutionRequests.getConsolidations().size();
    if (consolidationRequestsCount > maxConsolidationRequests) {
      return Optional.of(
          reject(
              "Parent execution requests has %d consolidation requests, max allowed %d",
              consolidationRequestsCount, maxConsolidationRequests));
    }

    final int maxBuilderDepositRequests =
        SpecConfigGloas.required(specConfig).getMaxBuilderDepositRequestsPerPayload();
    final int builderDepositRequestsCount =
        parentExecutionRequestsGloas.getBuilderDeposits().size();
    if (builderDepositRequestsCount > maxBuilderDepositRequests) {
      return Optional.of(
          reject(
              "Parent execution requests has %d builder deposit requests, max allowed %d",
              builderDepositRequestsCount, maxBuilderDepositRequests));
    }

    final int maxBuilderExitRequests =
        SpecConfigGloas.required(specConfig).getMaxBuilderExitRequestsPerPayload();
    final int builderExitRequestsCount = parentExecutionRequestsGloas.getBuilderExits().size();
    if (builderExitRequestsCount > maxBuilderExitRequests) {
      return Optional.of(
          reject(
              "Parent execution requests has %d builder exit requests, max allowed %d",
              builderExitRequestsCount, maxBuilderExitRequests));
    }

    return Optional.empty();
  }

  /**
   * Validates the execution payload bid's parent (defined by {@code bid.parent_block_hash}) against
   * the parent's state and payload status.
   *
   * <p>[New in Gloas:EIP7732] If the parent block is full, the parent payload must be verified (MAY
   * be queued until the parent payload is verified, i.e. SAVE_FOR_FUTURE).
   *
   * <p>[New in Gloas:EIP7732] If the parent is not full, the bid must build on the parent's
   * execution head, i.e. {@code bid.parent_block_hash == parent_state.latest_block_hash}.
   */
  private Optional<InternalValidationResult> validateExecutionPayloadBidParent(
      final SignedBeaconBlock block,
      final BeaconStateGloas parentState,
      final ExecutionPayloadBid executionPayloadBid) {
    final MiscHelpersGloas miscHelpersGloas =
        MiscHelpersGloas.required(spec.atSlot(block.getSlot()).miscHelpers());

    if (miscHelpersGloas.isBidBuildingOnFullParent(parentState, executionPayloadBid)) {
      final boolean parentExecutionPayloadVerified =
          gossipValidationHelper.isBlockHashKnown(
              executionPayloadBid.getParentBlockHash(), block.getParentRoot());
      if (!parentExecutionPayloadVerified) {
        return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
      }
      return Optional.empty();
    }

    if (!executionPayloadBid.getParentBlockHash().equals(parentState.getLatestBlockHash())) {
      return Optional.of(reject("Bid does not build on the parent's execution head"));
    }
    return Optional.empty();
  }

  synchronized EquivocationCheckResult performBlockEquivocationCheck(
      final boolean markAsReceived, final SignedBeaconBlock block) {
    final SlotAndProposer slotAndProposer = new SlotAndProposer(block);
    final Bytes32 blockRoot = block.getRoot();
    return Optional.ofNullable(receivedValidBlockRoots.get(slotAndProposer))
        .map(
            previouslySeenBlockRoot -> {
              if (previouslySeenBlockRoot.equals(blockRoot)) {
                return BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
              }
              return EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
            })
        .orElseGet(
            () -> {
              if (markAsReceived) {
                receivedValidBlockRoots.put(slotAndProposer, blockRoot);
              }
              return FIRST_BLOCK_FOR_SLOT_PROPOSER;
            });
  }

  private synchronized boolean reserveEquivocationSignatureAttempt(final SignedBeaconBlock block) {
    final Set<Bytes32> attemptedRoots =
        attemptedEquivocationRoots.computeIfAbsent(
            new SlotAndProposer(block), __ -> new LinkedHashSet<>());
    if (attemptedRoots.contains(block.getRoot())
        || attemptedRoots.size() >= MAX_EQUIVOCATION_SIGNATURE_ATTEMPTS) {
      return false;
    }
    attemptedRoots.add(block.getRoot());
    return true;
  }

  public enum EquivocationCheckResult {
    FIRST_BLOCK_FOR_SLOT_PROPOSER,
    BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER,
    EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      final SignedBeaconBlock block, final BeaconState postState) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignBlock(block.getMessage(), postState.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot, block.getProposerIndex(), block.getSignature(), postState);
  }

  public synchronized boolean isBlockEquivocating(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    return Optional.ofNullable(
            receivedValidBlockRoots.get(new SlotAndProposer(slot, proposerIndex)))
        .map(seenBlockRoot -> !seenBlockRoot.equals(blockRoot))
        .orElse(false);
  }

  private record SlotAndProposer(UInt64 slot, UInt64 proposerIndex) {
    public SlotAndProposer(final SignedBeaconBlock block) {
      this(block.getSlot(), block.getMessage().getProposerIndex());
    }
  }
}
