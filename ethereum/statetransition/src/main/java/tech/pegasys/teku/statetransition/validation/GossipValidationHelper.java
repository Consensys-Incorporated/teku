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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.AttestationValidationResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipValidationHelper {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final int maxOffsetTimeInMillis;
  private final AttestationStateSelector attestationStateSelector;

  public GossipValidationHelper(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.maxOffsetTimeInMillis = spec.getNetworkingConfig().getMaximumGossipClockDisparity();
    this.attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, metricsSystem);
  }

  public boolean isSlotFinalized(final UInt64 slot) {
    final UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return slot.isLessThanOrEqualTo(finalizedSlot);
  }

  public boolean isBeforeFinalizedSlot(final UInt64 slot) {
    final UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return slot.isLessThan(finalizedSlot);
  }

  public boolean isSlotFromFuture(final UInt64 slot) {
    final UInt64 maxTime = getCurrentTimeMillis().plus(maxOffsetTimeInMillis);
    final UInt64 maxCurrSlot =
        spec.getCurrentSlotFromTimeMillis(maxTime, recentChainData.getGenesisTimeMillis());
    return slot.isGreaterThan(maxCurrSlot);
  }

  public boolean hasSlotStarted(final UInt64 slot) {
    // Also prevents overflow when converting an extreme future slot to milliseconds.
    if (isSlotFromFuture(slot)) {
      return false;
    }

    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    return getCurrentTimeMillis().isGreaterThan(slotStartTimeMillis.plus(maxOffsetTimeInMillis));
  }

  public boolean isSlotCurrent(final UInt64 slot) {
    // Also prevents overflow when converting an extreme future slot to milliseconds.
    if (isSlotFromFuture(slot)) {
      return false;
    }

    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 slotEndTimeMillis = slotStartTimeMillis.plus(spec.getSlotDurationMillis(slot));
    final UInt64 currentTimeMillis = getCurrentTimeMillis();

    return currentTimeMillis.isGreaterThanOrEqualTo(
            slotStartTimeMillis.minusMinZero(maxOffsetTimeInMillis))
        && currentTimeMillis.isLessThanOrEqualTo(slotEndTimeMillis.plus(maxOffsetTimeInMillis));
  }

  public boolean isSignatureValidWithRespectToBuilderIndex(
      final Bytes signingRoot,
      final UInt64 builderIndex,
      final BLSSignature signature,
      final BeaconState state) {
    if (builderIndex.equals(BUILDER_INDEX_SELF_BUILD)) {
      return isSignatureValidWithRespectToProposerIndex(
          signingRoot, state.getLatestBlockHeader().getProposerIndex(), signature, state);
    }
    return spec.getBuilderPubKey(state, builderIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final Bytes signingRoot,
      final UInt64 proposerIndex,
      final BLSSignature signature,
      final BeaconState state) {
    return spec.getValidatorPubKey(state, proposerIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final DataColumnSidecarUtil.SignatureVerificationData signatureVerificationData) {
    return isSignatureValidWithRespectToProposerIndex(
        signatureVerificationData.signingRoot(),
        signatureVerificationData.proposerIndex(),
        signatureVerificationData.signature(),
        signatureVerificationData.state());
  }

  /**
   * Retrieve the state for the parent block, applying the epoch transition if required to be able
   * to calculate the expected proposer for block.
   */
  public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final UInt64 parentBlockSlot, final Bytes32 parentBlockRoot, final UInt64 slot) {
    final UInt64 firstSlotInBlockEpoch =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot));
    return parentBlockSlot.isLessThan(firstSlotInBlockEpoch)
        ? recentChainData.retrieveBlockState(
            new SlotAndBlockRoot(firstSlotInBlockEpoch, parentBlockRoot))
        : recentChainData.retrieveBlockState(parentBlockRoot);
  }

  public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final DataColumnSidecarUtil.StateRetrievalData stateRetrievalData) {
    return getParentStateInBlockEpoch(
        stateRetrievalData.parentBlockSlot(),
        stateRetrievalData.parentBlockRoot(),
        stateRetrievalData.slot());
  }

  /**
   * Retrieve the post state of the block, without advancing it to any later slot. Callers that have
   * already established that the state they need is at the block's own slot should use this rather
   * than looking the state up by slot and block root: the slot based lookup goes through the
   * checkpoint state task queue, which would only skip the slot processing after having taken that
   * queue's lock, searched the cache for an earlier state to rebase on and scheduled a task.
   */
  public SafeFuture<Optional<BeaconState>> getStateAtBlockRoot(final Bytes32 blockRoot) {
    return recentChainData.retrieveBlockState(blockRoot);
  }

  public boolean currentFinalizedCheckpointIsAncestorOfBlock(
      final UInt64 blockSlot, final Bytes32 blockParentRoot) {
    return spec.blockDescendsFromLatestFinalizedBlock(
        blockSlot,
        blockParentRoot,
        recentChainData.getStore(),
        recentChainData.getForkChoiceStrategy().orElseThrow());
  }

  @SuppressWarnings("unused")
  public boolean currentFinalizedCheckpointIsAncestorOfAttestationBlock(final Bytes32 blockRoot) {
    // All nodes in the proto-array descend from the finalized block, so no production validation
    // is needed for this rule. The reference-test executor overrides this method to model
    // generated tests with fake finalized checkpoint roots that cannot be represented in Store.
    return true;
  }

  public boolean isProposerTheExpectedProposer(
      final UInt64 proposerIndex, final UInt64 slot, final BeaconState postState) {
    final int expectedProposerIndex = spec.getBeaconProposerIndex(postState, slot);
    return expectedProposerIndex == proposerIndex.longValue();
  }

  public boolean isProposerTheExpectedProposer(
      final DataColumnSidecarUtil.ProposerValidationData proposerValidationData) {
    return isProposerTheExpectedProposer(
        proposerValidationData.proposerIndex(),
        proposerValidationData.slot(),
        proposerValidationData.postState());
  }

  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 blockRoot) {
    return recentChainData.getSlotForBlockRoot(blockRoot);
  }

  public InternalValidationResult validatePayloadStatus(
      final AttestationUtil attestationUtil,
      final AttestationData attestationData,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload) {
    final AttestationValidationResult payloadStatusValidationResult =
        attestationUtil.validatePayloadStatus(
            attestationData, getSlotForBlockRoot(attestationData.getBeaconBlockRoot()));
    // [REJECT] attestation.data.index == 0 if block.slot == attestation.data.slot.
    if (!payloadStatusValidationResult.isValid()) {
      return reject(payloadStatusValidationResult.getReason().orElse("Invalid payload status"));
    }

    final Bytes32 blockRoot = attestationData.getBeaconBlockRoot();
    if (spec.atSlot(attestationData.getSlot())
        .getForkChoiceUtil()
        .getFullPayloadVoteHint(attestationData.getIndex())) {
      // [REJECT] If attestation.data.index == 1 (payload present for a past block), the execution
      // payload for block passes validation.
      if (blockRootsWithInvalidExecutionPayload.contains(blockRoot)) {
        return InternalValidationResult.reject(
            "Execution payload for full payload attestation is invalid");
      }
      // [IGNORE] When attestation.data.index == 1 (payload present for a past block), the execution
      // payload for block has been seen.
      if (getRecentlyImportedExecutionPayload(blockRoot).isEmpty()) {
        return InternalValidationResult.SAVE_FOR_FUTURE;
      }
      // [IGNORE] The attested execution payload is optimistic.
      if (isExecutionPayloadOptimistic(blockRoot)) {
        return InternalValidationResult.SAVE_FOR_FUTURE;
      }
    }

    return InternalValidationResult.ACCEPT;
  }

  private boolean isExecutionPayloadOptimistic(final Bytes32 blockRoot) {
    return recentChainData
        .getForkChoiceStrategy()
        .flatMap(
            forkChoiceStrategy ->
                forkChoiceStrategy.getBlockData(
                    blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .map(ProtoNodeData::isOptimistic)
        .orElse(true);
  }

  public boolean isBlockAvailable(final Bytes32 blockRoot) {
    return recentChainData.containsBlock(blockRoot);
  }

  int getMaxOffsetTimeInMillis() {
    return maxOffsetTimeInMillis;
  }

  public SafeFuture<Optional<BeaconState>> getStateForAttestationValidation(
      final AttestationData attestationData) {
    return attestationStateSelector.getStateToValidate(attestationData);
  }

  public UInt64 getGenesisTime() {
    return recentChainData.getGenesisTime();
  }

  public UInt64 getCurrentTimeMillis() {
    return recentChainData.getStore().getTimeInMillis();
  }

  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return recentChainData.getForkChoiceStrategy().orElseThrow();
  }

  public SafeFuture<Optional<BeaconBlock>> retrieveBlockByRoot(final Bytes32 root) {
    return recentChainData.retrieveBlockByRoot(root);
  }

  public boolean isActiveBuilder(
      final UInt64 builderIndex, final BeaconState state, final UInt64 slot) {
    return PredicatesGloas.required(spec.atSlot(slot).predicates())
        .isActiveBuilder(state, builderIndex);
  }

  /**
   * Returns the RANDAO mix of the given state at the state's current epoch -- i.e.
   * get_randao_mix(state, get_current_epoch(state)).
   */
  public Bytes32 getRandaoMixForCurrentEpoch(final BeaconState state, final UInt64 slot) {
    final BeaconStateAccessors beaconStateAccessors = spec.atSlot(slot).beaconStateAccessors();
    return beaconStateAccessors.getRandaoMix(state, beaconStateAccessors.getCurrentEpoch(state));
  }

  /**
   * Returns true when {@code slot} is the current or the next slot, allowing for
   * MAXIMUM_GOSSIP_CLOCK_DISPARITY -- i.e. spec {@code is_current_or_next_slot}, which is defined
   * as {@code is_current_slot(slot) or is_current_slot(slot - 1)}.
   */
  public boolean isSlotCurrentOrNext(final UInt64 slot) {
    return isSlotCurrent(slot) || (!slot.isZero() && isSlotCurrent(slot.decrement()));
  }

  /**
   * Returns true when the block with the given {@code root} is a possible dependent block for the
   * epoch starting at {@code epochStartSlot} -- i.e. spec {@code is_valid_dependent_root}. That
   * holds when, on some branch, the block is or could become the latest block prior to the start of
   * the epoch.
   */
  public boolean isPossibleDependentRoot(final Bytes32 root, final UInt64 epochStartSlot) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    final Optional<UInt64> rootSlot = forkChoiceStrategy.blockSlot(root);
    if (rootSlot.isPresent() && rootSlot.get().isGreaterThanOrEqualTo(epochStartSlot)) {
      return false;
    }
    final UInt64 lastSlotBeforeEpoch = epochStartSlot.minusMinZero(ONE);
    // The root already is the latest block before the epoch on any branch where it has a child at
    // or after epochStartSlot. Rather than scanning every block in the store for such a child, walk
    // back from each chain head to the last slot before the epoch: that ancestor is the root
    // exactly when the root's next block on that branch sits at or after epochStartSlot. Heads
    // equal to the root are skipped, as reaching the root without traversing a child proves
    // nothing.
    final boolean hasChildAtOrAfterEpochStart =
        forkChoiceStrategy.getChainHeads(true).stream()
            .map(ProtoNodeData::getRoot)
            .filter(headRoot -> !headRoot.equals(root))
            .anyMatch(
                headRoot ->
                    forkChoiceStrategy
                        .getAncestor(headRoot, lastSlotBeforeEpoch)
                        .filter(root::equals)
                        .isPresent());
    if (hasChildAtOrAfterEpochStart) {
      return true;
    }
    // Otherwise the root could still become the latest block before the epoch if it is the head,
    // because the next block to extend it would be at or after epochStartSlot.
    return recentChainData.getBestBlockRoot().filter(root::equals).isPresent();
  }

  public boolean builderHasEnoughBalanceForBid(
      final UInt64 bidValue,
      final UInt64 builderIndex,
      final BeaconState state,
      final UInt64 slot) {
    return BeaconStateAccessorsGloas.required(spec.atSlot(slot).beaconStateAccessors())
        .canBuilderCoverBid(state, builderIndex, bidValue);
  }

  public boolean isBlockHashKnown(final Bytes32 blockHash, final Bytes32 blockRoot) {
    final Optional<Bytes32> maybeBlockHash =
        recentChainData.getExecutionBlockHashForBlockRoot(blockRoot);
    return maybeBlockHash.isPresent() && blockHash.equals(maybeBlockHash.get());
  }

  public Optional<SignedExecutionPayloadEnvelope> getRecentlyImportedExecutionPayload(
      final Bytes32 blockRoot) {
    return recentChainData.getStore().getExecutionPayloadIfAvailable(blockRoot);
  }

  public Optional<UInt64> getGasLimitForExecutionPayload(
      final Bytes32 blockRoot, final Bytes32 blockHash) {
    return recentChainData.getExecutionGasLimitForBlockRootAndHash(blockRoot, blockHash);
  }

  public boolean isBidCompatibleWithHead(final ExecutionPayloadBid bid) {
    final Optional<ChainHead> maybeHead = recentChainData.getChainHead();
    if (maybeHead.isEmpty()) {
      return false;
    }

    final ChainHead head = maybeHead.get();
    final ReadOnlyStore store = recentChainData.getStore();
    final Optional<SignedBeaconBlock> maybeHeadBlock = store.getBlockIfAvailable(head.getRoot());
    if (maybeHeadBlock.isEmpty()) {
      return false;
    }
    final Optional<ExecutionPayloadBid> maybeHeadBid =
        maybeHeadBlock
            .get()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .map(SignedExecutionPayloadBid::getMessage);
    if (maybeHeadBid.isEmpty()) {
      // A pre-Gloas head has no bid, so the first Gloas bid builds on its execution payload
      return bid.getParentBlockRoot().equals(head.getRoot())
          && bid.getParentBlockHash().equals(head.getExecutionBlockHash());
    }

    final ExecutionPayloadBid headBid = maybeHeadBid.get();
    final boolean buildsOnParentBlock = bid.getParentBlockRoot().equals(head.getParentRoot());
    final boolean buildsOnParentPayload =
        bid.getParentBlockHash().equals(headBid.getParentBlockHash());
    if (buildsOnParentBlock && buildsOnParentPayload) {
      return true;
    }
    if (!bid.getParentBlockRoot().equals(head.getRoot())) {
      return false;
    }

    final boolean buildsOnHeadPayload = bid.getParentBlockHash().equals(headBid.getBlockHash());
    if (getForkChoiceStrategy().shouldBuildOnFull(store, bid.getSlot(), head.getForkChoiceNode())) {
      return buildsOnHeadPayload;
    }
    return buildsOnParentPayload;
  }

  private static InternalValidationResult reject(final String reason) {
    return InternalValidationResult.create(ValidationResultCode.REJECT, reason);
  }
}
