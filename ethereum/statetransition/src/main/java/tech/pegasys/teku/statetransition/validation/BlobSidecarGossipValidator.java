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

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.BEST_CASE_NON_FINALIZED_EPOCHS;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

/**
 * This class supposed to implement gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/p2p-interface.md#the-gossip-domain-gossipsub">spec</a>
 */
public class BlobSidecarGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Set<SlotProposerIndexAndBlobIndex> receivedValidBlobSidecarInfoSet;
  private final Set<Bytes32> validSignedBlockHeaders;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final MiscHelpersDeneb miscHelpersDeneb;

  public static BlobSidecarGossipValidator create(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper validationHelper,
      final MiscHelpersDeneb miscHelpersDeneb) {

    final Optional<Integer> maybeMaxBlobsPerBlock = spec.getMaxBlobsPerBlockForHighestMilestone();

    final int validInfoSize = VALID_BLOCK_SET_SIZE * maybeMaxBlobsPerBlock.orElse(1);
    // It's not fatal if we miss something and we don't need finalized data
    final int validSignedBlockHeadersSize =
        spec.getGenesisSpec().getSlotsPerEpoch() * BEST_CASE_NON_FINALIZED_EPOCHS;

    return new BlobSidecarGossipValidator(
        spec,
        invalidBlockRoots,
        validationHelper,
        miscHelpersDeneb,
        LimitedSet.createSynchronizedLRU(validInfoSize),
        LimitedSet.createSynchronizedLRU(validSignedBlockHeadersSize));
  }

  @VisibleForTesting
  Set<SlotProposerIndexAndBlobIndex> getReceivedValidBlobSidecarInfoSet() {
    return receivedValidBlobSidecarInfoSet;
  }

  private BlobSidecarGossipValidator(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final MiscHelpersDeneb miscHelpersDeneb,
      final Set<SlotProposerIndexAndBlobIndex> receivedValidBlobSidecarInfoSet,
      final Set<Bytes32> validSignedBlockHeaders) {
    this.spec = spec;
    this.invalidBlockRoots = invalidBlockRoots;
    this.gossipValidationHelper = gossipValidationHelper;
    this.miscHelpersDeneb = miscHelpersDeneb;
    this.receivedValidBlobSidecarInfoSet = receivedValidBlobSidecarInfoSet;
    this.validSignedBlockHeaders = validSignedBlockHeaders;
  }

  public SafeFuture<InternalValidationResult> validate(final BlobSidecar blobSidecar) {

    final BeaconBlockHeader blockHeader = blobSidecar.getSignedBeaconBlockHeader().getMessage();

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, blob_sidecar.index)
     *  with valid header signature, sidecar inclusion proof, and kzg proof.
     */
    if (!isFirstValidForSlotProposerIndexAndBlobIndex(blobSidecar, blockHeader)) {
      LOG.trace("BlobSidecar is not the first valid for its slot and index. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    /*
     * [REJECT] The sidecar's index is consistent with `MAX_BLOBS_PER_BLOCK` -- i.e. `blob_sidecar.index < MAX_BLOBS_PER_BLOCK`.
     */
    final Optional<Integer> maxBlobsPerBlockAtSlot =
        spec.getMaxBlobsPerBlockAtSlot(blobSidecar.getSlot());

    if (maxBlobsPerBlockAtSlot.isEmpty()) {
      return completedFuture(reject("BlobSidecar's slot is pre-Deneb"));
    }
    if (blobSidecar.getIndex().isGreaterThanOrEqualTo(maxBlobsPerBlockAtSlot.get())) {
      return completedFuture(reject("BlobSidecar index is greater than MAX_BLOBS_PER_BLOCK"));
    }

    /*
     * [REJECT] The sidecar is for the correct subnet -- i.e. `compute_subnet_for_blob_sidecar(blob_sidecar.index) == subnet_id`.
     *
     * This rule is already implemented in
     * tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipManager.TopicSubnetIdAwareOperationProcessor
     */

    /*
     * [IGNORE]_ The sidecar is not from a future slot (with a `MAXIMUM_GOSSIP_CLOCK_DISPARITY` allowance) -- i.e. validate
     * that `block_header.slot <= current_slot` (a client MAY queue future sidecars for processing at the appropriate slot).
     */
    if (gossipValidationHelper.isSlotFromFuture(blockHeader.getSlot())) {
      LOG.trace("BlobSidecar is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] The sidecar is from a slot greater than the latest finalized slot -- i.e. validate that
     * `block_header.slot > compute_start_slot_at_epoch(store.finalized_checkpoint.epoch)`
     */
    if (gossipValidationHelper.isSlotFinalized(blockHeader.getSlot())) {
      LOG.trace("BlobSidecar is too old (slot already finalized)");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    // Optimization: If we have already completely verified BlobSidecar with the same
    // SignedBlockHeader, we can skip most steps and jump to shortened validation
    if (validSignedBlockHeaders.contains(blobSidecar.getSignedBeaconBlockHeader().hashTreeRoot())) {
      return validateBlobSidecarWithKnownValidHeader(blobSidecar, blockHeader);
    }

    /*
     * [REJECT] The proposer signature of `blob_sidecar.signed_block_header`, is valid with respect
     * to the `block_header.proposer_index` pubkey.
     *
     * Verified later after all checks not involving state are passed
     */

    /*
     * [IGNORE] The sidecar's block's parent (defined by `block_header.parent_root`) has been seen (via both gossip and
     * non-gossip sources) (a client MAY queue sidecars for processing once the parent block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(blockHeader.getParentRoot())) {
      LOG.trace(
          "BlobSidecar parent block is not available. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(blockHeader.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace("BlobSidecar parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The sidecar's block's parent (defined by `block_header.parent_root`) passes validation.
     */
    if (invalidBlockRoots.containsKey(blockHeader.getParentRoot())) {
      return completedFuture(reject("BlobSidecar block header has an invalid parent root"));
    }

    /*
     * [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by `block_header.parent_root`).
     */
    if (!blockHeader.getSlot().isGreaterThan(parentBlockSlot)) {
      return completedFuture(reject("Parent block is after BlobSidecar slot."));
    }

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e.
     * `get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root`.
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return completedFuture(
          reject("BlobSidecar block header does not descend from finalized checkpoint"));
    }

    /*
     * [REJECT] The sidecar's inclusion proof is valid as verified by `verify_blob_sidecar_inclusion_proof(blob_sidecar)`.
     */
    if (!miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(blobSidecar)) {
      return completedFuture(reject("BlobSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The sidecar's blob is valid as verified by
     * `verify_blob_kzg_proof(blob_sidecar.blob, blob_sidecar.kzg_commitment, blob_sidecar.kzg_proof)`.
     */
    if (!miscHelpersDeneb.verifyBlobKzgProof(blobSidecar)) {
      return completedFuture(reject("BlobSidecar does not pass kzg validation"));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            parentBlockSlot, blockHeader.getParentRoot(), blockHeader.getSlot())
        .thenApply(
            maybePostState -> {
              /*
               * [REJECT] The sidecar is proposed by the expected `proposer_index` for the block's slot in the context of the current
               *  shuffling (defined by `block_header.parent_root`/`block_header.slot`).
               *
               * If the `proposer_index` cannot immediately be verified against the expected shuffling, the sidecar MAY be queued for
               * later processing while proposers for the block's branch are calculated -- in such a case
               * _do not_ `REJECT`, instead `IGNORE` this message.
               */
              if (maybePostState.isEmpty()) {
                LOG.trace(
                    "BlobSidecar block header state wasn't available. Must have been pruned by finalized.");
                return InternalValidationResult.IGNORE;
              }
              final BeaconState postState = maybePostState.get();
              if (!gossipValidationHelper.isProposerTheExpectedProposer(
                  blockHeader.getProposerIndex(), blockHeader.getSlot(), postState)) {
                return reject(
                    "BlobSidecar block header proposed by incorrect proposer (%s).",
                    blockHeader.getProposerIndex());
              }

              /*
               * [REJECT] The proposer signature of `blob_sidecar.signed_block_header`, is valid
               * with respect to the `block_header.proposer_index` pubkey.
               */
              if (!verifyBlockHeaderSignature(postState, blobSidecar)) {
                return reject("BlobSidecar block header signature is invalid.");
              }

              /*
               * Checking it again at the very end because whole method is not synchronized
               *
               * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, blob_sidecar.index)
               *  with valid header signature, sidecar inclusion proof, and kzg proof.
               */
              if (!markForEquivocation(blockHeader, blobSidecar.getIndex())) {
                return ignore(
                    "BlobSidecar is not the first valid for its slot and index. It will be dropped.");
              }

              validSignedBlockHeaders.add(blobSidecar.getSignedBeaconBlockHeader().hashTreeRoot());

              return ACCEPT;
            });
  }

  private boolean markForEquivocation(final BeaconBlockHeader blockHeader, final UInt64 index) {
    return receivedValidBlobSidecarInfoSet.add(
        new SlotProposerIndexAndBlobIndex(
            blockHeader.getSlot(), blockHeader.getProposerIndex(), index));
  }

  public boolean markForEquivocation(final BlobSidecar blobSidecar) {
    return markForEquivocation(
        blobSidecar.getSignedBeaconBlockHeader().getMessage(), blobSidecar.getIndex());
  }

  private SafeFuture<InternalValidationResult> validateBlobSidecarWithKnownValidHeader(
      final BlobSidecar blobSidecar, final BeaconBlockHeader blockHeader) {

    blobSidecar.markSignatureAsValidated();

    /*
     * [REJECT] The sidecar's inclusion proof is valid as verified by `verify_blob_sidecar_inclusion_proof(blob_sidecar)`.
     */
    if (!miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(blobSidecar)) {
      return completedFuture(reject("BlobSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The sidecar's blob is valid as verified by
     * `verify_blob_kzg_proof(blob_sidecar.blob, blob_sidecar.kzg_commitment, blob_sidecar.kzg_proof)`.
     */
    if (!miscHelpersDeneb.verifyBlobKzgProof(blobSidecar)) {
      return completedFuture(reject("BlobSidecar does not pass kzg validation"));
    }

    // This can be changed between two received BlobSidecars from one block, so checking
    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e.
     * `get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root`.
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return completedFuture(
          reject("BlobSidecar block header does not descend from finalized checkpoint"));
    }

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, blob_sidecar.index)
     *  with valid header signature, sidecar inclusion proof, and kzg proof.
     */
    if (!markForEquivocation(blockHeader, blobSidecar.getIndex())) {
      return SafeFuture.completedFuture(
          ignore("BlobSidecar is not the first valid for its slot and index. It will be dropped."));
    }

    return SafeFuture.completedFuture(ACCEPT);
  }

  private boolean verifyBlockHeaderSignature(
      final BeaconState state, final BlobSidecar blobSidecar) {
    final SignedBeaconBlockHeader signedBlockHeader = blobSidecar.getSignedBeaconBlockHeader();
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(state),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlockHeader.getMessage(), domain);

    final boolean result =
        gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            signingRoot,
            signedBlockHeader.getMessage().getProposerIndex(),
            signedBlockHeader.getSignature(),
            state);

    if (result) {
      blobSidecar.markSignatureAsValidated();
    }

    return result;
  }

  private boolean isFirstValidForSlotProposerIndexAndBlobIndex(
      final BlobSidecar blobSidecar, final BeaconBlockHeader blockHeader) {
    return !receivedValidBlobSidecarInfoSet.contains(
        new SlotProposerIndexAndBlobIndex(
            blockHeader.getSlot(), blockHeader.getProposerIndex(), blobSidecar.getIndex()));
  }

  record SlotProposerIndexAndBlobIndex(UInt64 slot, UInt64 proposerIndex, UInt64 blobIndex) {}
}
