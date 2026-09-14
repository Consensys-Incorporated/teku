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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.util.ForkChoiceUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;

public class ForkChoiceUtil {
  private static final Logger LOG = LogManager.getLogger();

  protected final SpecConfig specConfig;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final EpochProcessor epochProcessor;
  protected final AttestationUtil attestationUtil;
  protected final MiscHelpers miscHelpers;

  public ForkChoiceUtil(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.epochProcessor = epochProcessor;
    this.attestationUtil = attestationUtil;
    this.miscHelpers = miscHelpers;
  }

  protected UInt64 getCurrentSlot(final ReadOnlyStore store) {
    return miscHelpers.computeSlotAtTime(store.getGenesisTime(), store.getTimeSeconds());
  }

  private UInt64 computeSlotsSinceEpochStart(final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final UInt64 epochStartSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    return slot.minus(epochStartSlot);
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public Optional<Bytes32> getAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 root, final UInt64 slot) {
    return forkChoiceStrategy.getAncestor(root, slot);
  }

  /**
   * is_ancestor
   *
   * <p>Returns whether {@code ancestor} is an ancestor of {@code node}, i.e. {@code ancestor} lies
   * on the chain from {@code node} at {@code ancestor}'s block slot.
   *
   * <p>This base (phase0) form is the {@code is_ancestor#phase0} spec anchor and the default
   * overridden by {@code ForkChoiceUtilGloas#isAncestor}. Fast confirmation calls it explicitly
   * when computing attestation support. Protoarray fork-choice scoring instead realizes the same
   * ancestry relationship by propagating node weights to parents.
   */
  public boolean isAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final ForkChoiceNode node,
      final ForkChoiceNode ancestor) {
    return forkChoiceStrategy
        .blockSlot(ancestor.blockRoot())
        .flatMap(ancestorSlot -> forkChoiceStrategy.getAncestorNode(node, ancestorSlot))
        .map(ancestor::equals)
        .orElse(false);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot,
      final UInt64 step,
      final UInt64 count) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    // minus(ONE) because the start block is included
    final UInt64 endSlot = startSlot.plus(step.times(count)).minusMinZero(1);
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().compareTo(startSlot) > 0) {
      maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
    return roots;
  }

  /**
   * @param forkChoiceStrategy the object that stores information on forks and block roots
   * @param root the root that dictates the block/fork that we walk backwards from
   * @param startSlot the slot (exclusive) until which we walk the chain backwards
   * @return every block root from root (inclusive) to start slot (exclusive) traversing the chain
   *     backwards
   */
  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().isGreaterThan(startSlot)) {
      maybeAddRoot(startSlot, UInt64.ONE, roots, UInt64.MAX_VALUE, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    return roots;
  }

  /**
   * is_shuffling_stable
   *
   * @return {@code true} when {@code slot} is NOT at an epoch boundary.
   */
  public boolean isShufflingStable(final UInt64 slot) {
    return !slot.mod(specConfig.getSlotsPerEpoch()).isZero();
  }

  public boolean isFinalizationOk(final ReadOnlyStore store, final UInt64 slot) {
    final UInt64 epochsSinceFinalization =
        miscHelpers
            .computeEpochAtSlot(slot)
            .minusMinZero(store.getFinalizedCheckpoint().getEpoch());
    return epochsSinceFinalization.isLessThanOrEqualTo(
        specConfig.getReorgMaxEpochsSinceFinalization());
  }

  /** Spec reference: is_proposing_on_time. */
  public boolean isProposingOnTime(final ReadOnlyStore store, final UInt64 slot) {
    final UInt64 slotStartTimeMillis =
        miscHelpers.computeTimeMillisAtSlot(store.getGenesisTimeMillis(), slot);
    final int timelinessLimit = getProposerReorgCutoffMillis();
    final UInt64 currentTimeMillis = store.getTimeInMillis();
    final boolean isTimely =
        currentTimeMillis.minusMinZero(slotStartTimeMillis).isLessThanOrEqualTo(timelinessLimit);
    LOG.debug(
        "Check ProposingOnTime for slot {}, slot start time is {} ms and current time is {} ms, limit is {} ms result: {}",
        slot,
        slotStartTimeMillis,
        currentTimeMillis,
        timelinessLimit,
        isTimely);
    return isTimely;
  }

  /** Spec reference: get_proposer_head. */
  public ForkChoiceNode getProposerHead(
      final ForkChoiceReorgContext context, final ForkChoiceNode headNode, final UInt64 slot) {
    LOG.debug("start getProposerHead");
    final ReadOnlyStore store = context.getStore();
    final boolean isProposerBoostActive = isProposerBoostActive(store, headNode.blockRoot());
    final boolean isProposerStable = isProposerStable(slot);
    final boolean isFinalizationOk = isFinalizationOk(store, slot);
    final boolean isProposingOnTime = isProposingOnTime(store, slot);
    final boolean isHeadLate = isHeadLate(context.getBlockTimeliness(headNode.blockRoot()));
    final Optional<SignedBeaconBlock> maybeHead = store.getBlockIfAvailable(headNode.blockRoot());
    if (!isHeadLate
        || !isProposerStable
        || !isFinalizationOk
        || !isProposingOnTime
        || isProposerBoostActive
        || maybeHead.isEmpty()) {
      LOG.debug(
          "getProposerHead - return headRoot - isHeadLate {}, isProposerStable {}, isFinalizationOk {}, isProposingOnTime {}, isProposerBoostActive {}, head.isEmpty {}",
          isHeadLate,
          isProposerStable,
          isFinalizationOk,
          isProposingOnTime,
          isProposerBoostActive,
          maybeHead.isEmpty());
      return headNode;
    }

    final SignedBeaconBlock head = maybeHead.orElseThrow();
    final boolean isFfgCompetitive =
        isFfgCompetitive(store, headNode.blockRoot(), head.getParentRoot());
    final boolean isParentSlotOk = isParentSlotOk(store, head);
    final boolean isCurrentSlotOk = isCurrentSlotOk(head, slot);
    if (!isFfgCompetitive || !isParentSlotOk || !isCurrentSlotOk) {
      LOG.debug(
          "getProposerHead - return headRoot - isFfgCompetitive {}, isParentSlotOk {}, isCurrentSlotOk {}",
          isFfgCompetitive,
          isParentSlotOk,
          isCurrentSlotOk);
      return headNode;
    }

    final boolean isHeadWeak = isHeadWeak(store, headNode.blockRoot(), store.getReorgThreshold());
    final boolean isParentStrong = isParentStrong(store, head, store.getParentThreshold());
    if (isHeadWeak && isParentStrong) {
      LOG.debug("getProposerHead - return parentRoot - isHeadWeak true && isParentStrong true");
      return context
          .getStore()
          .getForkChoiceStrategy()
          .getParentBeaconBlockNode(headNode)
          .orElse(headNode);
    }

    LOG.debug(
        "getProposerHead - return headRoot - isHeadWeak {}, isParentStrong {}",
        isHeadWeak,
        isParentStrong);
    return headNode;
  }

  /** Spec reference: should_override_forkchoice_update. */
  public boolean shouldOverrideForkChoiceUpdate(
      final ForkChoiceReorgContext context, final Bytes32 headRoot, final UInt64 headSlot) {
    final ReadOnlyStore store = context.getStore();
    final Optional<SignedBeaconBlock> maybeHead = store.getBlockIfAvailable(headRoot);
    if (maybeHead.isEmpty()) {
      LOG.debug("shouldOverrideForkChoiceUpdate head - maybeHead empty.");
      return false;
    }
    if (!isHeadLate(context.getBlockTimeliness(headRoot))) {
      LOG.debug("shouldOverrideForkChoiceUpdate head - is not late.");
      return false;
    }

    final SignedBeaconBlock head = maybeHead.orElseThrow();
    final UInt64 currentSlot = getCurrentSlot(store);
    final UInt64 proposalSlot = headSlot.increment();
    // Unlike getProposerHead, this Teku-only optimization keeps the shuffling stability check at
    // every milestone: preparing a payload on the parent is only a latency win, so it stays
    // conservative at epoch boundaries.
    final boolean isShufflingStable = isShufflingStable(proposalSlot);
    final boolean isFinalizationOk = isFinalizationOk(store, proposalSlot);
    final boolean isFfgCompetitive = isFfgCompetitive(store, headRoot, head.getParentRoot());
    final Optional<UInt64> maybeParentSlot =
        store.getForkChoiceStrategy().blockSlot(head.getParentRoot());
    if (!isShufflingStable || !isFinalizationOk || !isFfgCompetitive || maybeParentSlot.isEmpty()) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate isShufflingStable {}, isFinalizationOk {}, isFfgCompetitive {}, maybeParentSlot {}",
          isShufflingStable,
          isFinalizationOk,
          isFfgCompetitive,
          maybeParentSlot);
      return false;
    }

    if (!shouldOverrideFcuCheckWeights(context, head, headRoot, proposalSlot, currentSlot)) {
      return false;
    }

    return shouldOverrideFcuCheckProposerPreState(context, proposalSlot, head.getParentRoot());
  }

  boolean shouldOverrideFcuCheckWeights(
      final ForkChoiceReorgContext context,
      final SignedBeaconBlock head,
      final Bytes32 headRoot,
      final UInt64 proposalSlot,
      final UInt64 currentSlot) {
    final ReadOnlyStore store = context.getStore();
    final boolean isProposingOnTime = isProposingOnTime(store, proposalSlot);
    final boolean isCurrentTimeOk =
        head.getSlot().equals(currentSlot)
            || (currentSlot.equals(proposalSlot) && isProposingOnTime);
    final boolean isParentSlotOk = isParentSlotOk(store, head);
    if (!isParentSlotOk || !isCurrentTimeOk) {
      LOG.debug(
          "shouldOverrideForkChoiceUpdate isParentSlotOk {}, isCurrentTimeOk {}",
          isParentSlotOk,
          isCurrentTimeOk);
      return false;
    }
    if (currentSlot.isGreaterThan(head.getSlot())) {
      final boolean isHeadWeak = isHeadWeak(store, headRoot, store.getReorgThreshold());
      final boolean isParentStrong = isParentStrong(store, head, store.getParentThreshold());
      if (!isHeadWeak || !isParentStrong) {
        LOG.debug(
            "shouldOverrideForkChoiceUpdate isHeadWeak {}, isParentStrong {}",
            isHeadWeak,
            isParentStrong);
        return false;
      }
    }
    return true;
  }

  boolean shouldOverrideFcuCheckProposerPreState(
      final ForkChoiceReorgContext context, final UInt64 proposalSlot, final Bytes32 parentRoot) {
    LOG.debug("Need parent state");
    final Optional<BeaconState> maybeParentState =
        context.getStore().getBlockStateIfAvailable(parentRoot);
    if (maybeParentState.isEmpty()) {
      LOG.debug("shouldOverrideForkChoice could not retrieve parent state from cache");
      return false;
    }

    try {
      final BeaconState proposerPreState =
          context.processSlots(maybeParentState.get(), proposalSlot);
      final int proposerIndex = getProposerIndex(proposerPreState);
      if (!context.isValidatorConnected(proposerIndex, proposalSlot)) {
        LOG.debug(
            "shouldOverrideForkChoiceUpdate isValidatorConnected({}) {}, ", proposerIndex, false);
        return false;
      }
      return true;
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.trace("Failed to process", e);
      return false;
    }
  }

  boolean isParentSlotOk(final ReadOnlyStore store, final SignedBeaconBlock head) {
    final Optional<UInt64> maybeParentSlot =
        store.getForkChoiceStrategy().blockSlot(head.getParentRoot());
    return maybeParentSlot
        .map(parentSlot -> parentSlot.increment().equals(head.getSlot()))
        .orElse(false);
  }

  boolean isCurrentSlotOk(final SignedBeaconBlock head, final UInt64 proposalSlot) {
    return proposalSlot.equals(head.getSlot().increment());
  }

  /**
   * Whether the proposer for {@code slot} is already fixed, so that re-orging the previous block
   * cannot change it.
   *
   * <p>Before Fulu the proposer is only known to be stable away from an epoch boundary, so this
   * falls back to {@code is_shuffling_stable}.
   */
  protected boolean isProposerStable(final UInt64 slot) {
    return isShufflingStable(slot);
  }

  boolean isProposerBoostActive(final ReadOnlyStore store, final Bytes32 headRoot) {
    return store.getProposerBoostRoot().map(root -> !root.equals(headRoot)).orElse(false);
  }

  boolean isFfgCompetitive(
      final ReadOnlyStore store, final Bytes32 headRoot, final Bytes32 parentRoot) {
    return store.isFfgCompetitive(headRoot, parentRoot).orElse(false);
  }

  /**
   * Computes block timeliness based on the arrival time relative to the slot start.
   *
   * <p>Spec reference: record_block_timeliness
   *
   * @param blockSlot the slot of the block
   * @param currentSlot the current slot
   * @param millisIntoSlot milliseconds elapsed since the slot start
   * @return boolean array of timeliness values. Pre-Gloas: single element (attestation deadline).
   *     Gloas: two elements (attestation deadline, PTC deadline).
   */
  public BlockTimeliness computeBlockTimeliness(
      final UInt64 blockSlot, final UInt64 currentSlot, final int millisIntoSlot) {
    final int timelinessLimit = getAttestationDueMillis();
    final boolean isTimely = blockSlot.equals(currentSlot) && timelinessLimit > millisIntoSlot;
    return new BlockTimeliness(isTimely, false);
  }

  public static boolean isHeadLate(final Optional<BlockTimeliness> blockTimeliness) {
    return blockTimeliness.filter(timeliness -> !timeliness.isTimelyAttestation).isPresent();
  }

  private void maybeAddRoot(
      final UInt64 startSlot,
      final UInt64 step,
      final NavigableMap<UInt64, Bytes32> roots,
      final UInt64 endSlot,
      final Bytes32 root,
      final Optional<UInt64> maybeSlot) {
    maybeSlot.ifPresent(
        slot -> {
          if (slot.compareTo(endSlot) <= 0
              && slot.compareTo(startSlot) >= 0
              && slot.minus(startSlot).mod(step).equals(UInt64.ZERO)) {
            roots.put(slot, root);
          }
        });
  }

  // Fork Choice Event Handlers

  /**
   * The implementation differs from the spec in that the time parameter is in milliseconds instead
   * of seconds. This allows the time to be stored in milliseconds which allows for more
   * fine-grained time calculations if required.
   *
   * @param store the store to update
   * @param timeMillis onTick time in milliseconds
   */
  public void onTick(final MutableStore store, final UInt64 timeMillis) {
    // To be extra safe check both time and genesisTime, although time should always be >=
    // genesisTime
    if (store.getTimeInMillis().isGreaterThan(timeMillis)
        || store.getGenesisTimeMillis().isGreaterThan(timeMillis)) {
      return;
    }
    final UInt64 previousSlot = getCurrentSlot(store);
    final UInt64 newSlot =
        miscHelpers.computeSlotAtTimeMillis(store.getGenesisTimeMillis(), timeMillis);
    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    final UInt64 newEpoch = miscHelpers.computeEpochAtSlot(newSlot);

    // Catch up on any missed epoch transitions
    for (UInt64 currentEpoch = previousEpoch.plus(1);
        currentEpoch.isLessThanOrEqualTo(newEpoch);
        currentEpoch = currentEpoch.plus(1)) {
      final UInt64 firstSlotOfEpoch = miscHelpers.computeStartSlotAtEpoch(currentEpoch);
      final UInt64 slotStartTime =
          miscHelpers.computeTimeMillisAtSlot(store.getGenesisTimeMillis(), firstSlotOfEpoch);
      processOnTick(store, slotStartTime);
    }
    // Catch up final part
    processOnTick(store, timeMillis);
  }

  private void processOnTick(final MutableStore store, final UInt64 timeMillis) {
    final UInt64 previousSlot = getCurrentSlot(store);

    // Update store time
    store.setTimeMillis(timeMillis);

    final UInt64 currentSlot = getCurrentSlot(store);

    if (currentSlot.isGreaterThan(previousSlot)) {
      store.removeProposerBoostRoot();
    }

    // Not a new epoch, return
    if (!(currentSlot.isGreaterThan(previousSlot)
        && computeSlotsSinceEpochStart(currentSlot).isZero())) {
      return;
    }

    final UInt64 previousEpoch = miscHelpers.computeEpochAtSlot(previousSlot);
    for (ProtoNodeData nodeData : store.getForkChoiceStrategy().getChainHeads(true)) {
      if (miscHelpers.computeEpochAtSlot(nodeData.getSlot()).equals(previousEpoch)) {
        store.pullUpBlockCheckpoints(nodeData.getRoot());
        updateCheckpoints(
            store,
            nodeData.getCheckpoints().getUnrealizedJustifiedCheckpoint(),
            nodeData.getCheckpoints().getUnrealizedFinalizedCheckpoint(),
            nodeData.isOptimistic());
      }
    }
  }

  private void updateCheckpoints(
      final MutableStore store,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final boolean isBlockOptimistic) {
    if (justifiedCheckpoint.getEpoch().isGreaterThan(store.getJustifiedCheckpoint().getEpoch())) {
      store.setJustifiedCheckpoint(justifiedCheckpoint);
    }
    if (finalizedCheckpoint.getEpoch().isGreaterThan(store.getFinalizedCheckpoint().getEpoch())) {
      store.setFinalizedCheckpoint(finalizedCheckpoint, isBlockOptimistic);
    }
  }

  @CheckReturnValue
  public SafeFuture<AttestationProcessingResult> validateAsync(
      final Fork fork,
      final ReadOnlyStore store,
      final ValidatableAttestation validatableAttestation,
      final Optional<BeaconState> maybeState,
      final AsyncBLSSignatureVerifier asyncSignatureVerifier) {
    Attestation attestation = validatableAttestation.getAttestation();
    return validateOnAttestation(store, attestation.getData())
        .ifSuccessfulAsync(
            () -> {
              if (maybeState.isEmpty()) {
                return SafeFuture.completedFuture(AttestationProcessingResult.UNKNOWN_BLOCK);
              } else {
                return attestationUtil.isValidIndexedAttestationAsync(
                    fork, maybeState.get(), validatableAttestation, asyncSignatureVerifier);
              }
            })
        .thenApply(
            result ->
                result.ifSuccessful(
                    () -> checkIfAttestationShouldBeSavedForFuture(store, attestation)));
  }

  private AttestationProcessingResult validateOnAttestation(
      final ReadOnlyStore store, final AttestationData attestationData) {

    UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(getCurrentSlot(store));
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();

    return validateOnAttestation(forkChoiceStrategy, currentEpoch, attestationData);
  }

  public AttestationProcessingResult validateOnAttestation(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final AttestationData attestationData) {
    final Checkpoint target = attestationData.getTarget();

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    final UInt64 previousEpoch =
        currentEpoch.isGreaterThan(SpecConfig.GENESIS_EPOCH)
            ? currentEpoch.minus(UInt64.ONE)
            : SpecConfig.GENESIS_EPOCH;

    if (!target.getEpoch().equals(previousEpoch) && !target.getEpoch().equals(currentEpoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(miscHelpers.computeEpochAtSlot(attestationData.getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    final Optional<UInt64> blockSlot =
        forkChoiceStrategy.blockSlot(attestationData.getBeaconBlockRoot());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestationData.getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // LMD vote must be consistent with FFG vote target
    final UInt64 targetSlot = miscHelpers.computeStartSlotAtEpoch(target.getEpoch());
    if (getAncestor(forkChoiceStrategy, attestationData.getBeaconBlockRoot(), targetSlot)
        .map(ancestorRoot -> !ancestorRoot.equals(target.getRoot()))
        .orElse(true)) {
      return AttestationProcessingResult.invalid(
          "LMD vote must be consistent with FFG vote target");
    }

    return AttestationProcessingResult.SUCCESSFUL;
  }

  private AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      final ReadOnlyStore store, final Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    final UInt64 currentSlot = getCurrentSlot(store);
    if (currentSlot.isLessThan(attestation.getData().getSlot())) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    if (currentSlot.equals(attestation.getData().getSlot())) {
      return AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    final UInt64 epochStartSlot =
        miscHelpers.computeStartSlotAtEpoch(attestation.getData().getTarget().getEpoch());
    if (currentSlot.isLessThan(epochStartSlot)) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  /**
   * Stores block and associated blobSidecars if any into the store
   *
   * @param store Store to apply block to
   * @param signedBlock Block
   * @param postState State after applying this block
   * @param isBlockOptimistic optimistic/non-optimistic
   * @param blobSidecars BlobSidecars
   * @param earliestBlobSidecarsSlot not required even post-Deneb, saved only if the new value is
   *     smaller
   */
  public void applyBlockToStore(
      final MutableStore store,
      final SignedBeaconBlock signedBlock,
      final BeaconState postState,
      final boolean isBlockOptimistic,
      final Optional<List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> earliestBlobSidecarsSlot) {

    BlockCheckpoints blockCheckpoints = epochProcessor.calculateBlockCheckpoints(postState);

    // If block is from a prior epoch, pull up the post-state to next epoch to realize new finality
    // info
    if (miscHelpers
        .computeEpochAtSlot(signedBlock.getSlot())
        .isLessThan(miscHelpers.computeEpochAtSlot(getCurrentSlot(store)))) {
      blockCheckpoints = blockCheckpoints.realizeNextEpoch();
    }

    updateCheckpoints(
        store,
        blockCheckpoints.getJustifiedCheckpoint(),
        blockCheckpoints.getFinalizedCheckpoint(),
        isBlockOptimistic);

    // Add new block to store
    store.putBlockAndState(
        signedBlock, postState, blockCheckpoints, blobSidecars, earliestBlobSidecarsSlot);
  }

  public SafeFuture<Optional<BeaconState>> retrievePreStateRequiredOnBlock(
      final ReadOnlyStore store, final SignedBeaconBlock block) {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getSlot(), block.getParentRoot());
    return store.retrieveBlockState(slotAndBlockRoot);
  }

  public void applyExecutionPayloadToStore(
      final MutableStore store,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final boolean executionOptimistic) {
    // No-op until the runtime wiring switches to the Gloas payload path.
  }

  private UInt64 getFinalizedCheckpointStartSlot(final ReadOnlyStore store) {
    final UInt64 finalizedEpoch = store.getFinalizedCheckpoint().getEpoch();
    return miscHelpers.computeStartSlotAtEpoch(finalizedEpoch);
  }

  public BlockImportResult checkOnBlockConditions(
      final SignedBeaconBlock block, final BeaconState blockSlotState, final ReadOnlyStore store) {
    final UInt64 blockSlot = block.getSlot();
    if (blockSlotState == null) {
      return BlockImportResult.FAILED_UNKNOWN_PARENT;
    }
    if (!blockSlotState.getSlot().equals(blockSlot)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockSlot.isGreaterThan(SpecConfig.GENESIS_SLOT)
        && !beaconStateAccessors
            .getBlockRootAtSlot(blockSlotState, blockSlot.minus(1))
            .equals(block.getParentRoot())) {
      // Block is at same slot as its parent or the parent root doesn't match the state
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE;
    }
    if (!blockDescendsFromLatestFinalizedBlock(
        block.getSlot(), block.getParentRoot(), store, store.getForkChoiceStrategy())) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }
    // Successful so far
    return BlockImportResult.successful(block);
  }

  private boolean blockIsFromFuture(final ReadOnlyStore store, final UInt64 blockSlot) {
    return getCurrentSlot(store).compareTo(blockSlot) < 0;
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final UInt64 blockSlot,
      final Bytes32 parentBlockRoot,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();

    // Make sure this block's slot is after the latest finalized slot
    final UInt64 finalizedEpochStartSlot = getFinalizedCheckpointStartSlot(store);
    return blockIsAfterLatestFinalizedSlot(blockSlot, finalizedEpochStartSlot)
        && hasAncestorAtSlot(
            forkChoiceStrategy,
            parentBlockRoot,
            finalizedEpochStartSlot,
            finalizedCheckpoint.getRoot());
  }

  private boolean blockIsAfterLatestFinalizedSlot(
      final UInt64 blockSlot, final UInt64 finalizedEpochStartSlot) {
    return blockSlot.compareTo(finalizedEpochStartSlot) > 0;
  }

  private boolean hasAncestorAtSlot(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 slot,
      final Bytes32 ancestorRoot) {
    return getAncestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }

  public boolean canOptimisticallyImport(final ReadOnlyStore store, final SignedBeaconBlock block) {
    // A block can be optimistically imported either if it's parent contains a non-default payload
    // or if it is at least `SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY` before the current slot.
    // This is to avoid the merge block referencing a non-existent eth1 parent block and causing
    // all nodes to switch to optimistic sync mode.
    if (isExecutionBlock(store, block)) {
      return true;
    }
    return isBellatrixBlockOld(store, block.getSlot());
  }

  /* non-functional in early forks */
  public Optional<UInt64> getEarliestAvailabilityWindowSlotBeforeBlock(
      final Spec spec, final ReadOnlyStore store, final UInt64 slot) {
    return Optional.empty();
  }

  private boolean isBellatrixBlockOld(final ReadOnlyStore store, final UInt64 blockSlot) {
    final Optional<SpecConfigBellatrix> maybeConfig = specConfig.toVersionBellatrix();
    if (maybeConfig.isEmpty()) {
      return false;
    }
    return blockSlot
        .plus(maybeConfig.get().getSafeSlotsToImportOptimistically())
        .isLessThanOrEqualTo(getCurrentSlot(store));
  }

  @VisibleForTesting
  // get_slot_component_duration_ms
  protected int getSlotComponentDurationMillis(final int basisPoints) {
    return (basisPoints * specConfig.getSlotDurationMillis()) / 10_000;
  }

  public int getAttestationDueMillis() {
    return getSlotComponentDurationMillis(specConfig.getAttestationDueBps());
  }

  public int getSyncMessageDueMillis() {
    final SpecConfigAltair configAltair = SpecConfigAltair.required(specConfig);
    return getSlotComponentDurationMillis(configAltair.getSyncMessageDueBps());
  }

  public int getAggregateDueMillis() {
    return getSlotComponentDurationMillis(specConfig.getAggregateDueBps());
  }

  public int getContributionDueMillis() {
    final SpecConfigAltair configAltair = SpecConfigAltair.required(specConfig);
    return getSlotComponentDurationMillis(configAltair.getContributionDueBps());
  }

  public int getProposerReorgCutoffMillis() {
    return getSlotComponentDurationMillis(specConfig.getProposerReorgCutoffBps());
  }

  // get_payload_attestation_due_ms
  public Optional<Integer> getPayloadAttestationDueMillis() {
    return Optional.empty();
  }

  // get_payload_due_ms
  public Optional<Integer> getPayloadDueMillis() {
    return Optional.empty();
  }

  /**
   * Returns true if data availability must complete before recording block timeliness.
   *
   * <p>For Fulu, the spec records timeliness post-DA (PeerDAS). For all other forks, timeliness is
   * recorded at block body arrival.
   */
  public boolean isDataAvailabilityRequiredForTimeliness() {
    return false;
  }

  private boolean isExecutionBlock(final ReadOnlyStore store, final SignedBeaconBlock block) {
    // post-Bellatrix: always true
    final BeaconBlockBody body = block.getMessage().getBody();
    if (body.toVersionCapella().isPresent() || body.toBlindedVersionCapella().isPresent()) {
      return true;
    }
    final Optional<Bytes32> parentExecutionRoot =
        store.getForkChoiceStrategy().executionBlockHash(block.getParentRoot());
    return parentExecutionRoot.isPresent() && !parentExecutionRoot.get().isZero();
  }

  /**
   * Determines whether a validator's vote should be updated based on the attestation.
   *
   * <p>Pre-Gloas uses epoch-based comparison; Gloas overrides with slot-based comparison.
   *
   * @param vote the current vote tracker for the validator
   * @param targetEpoch the target epoch of the attestation
   * @param slot the slot of the attestation
   * @return true if the vote should be updated
   */
  public boolean shouldUpdateVote(
      final VoteTracker vote, final UInt64 targetEpoch, final UInt64 slot) {
    return targetEpoch.isGreaterThan(miscHelpers.computeEpochAtSlot(vote.getNextSlot()))
        || vote.equals(VoteTracker.DEFAULT);
  }

  /**
   * Extracts the forkchoice-side FULL-node hint from attestation data.
   *
   * <p>Pre-Gloas forks do not carry payload-status information in attestations, so the default is
   * always false. Gloas overrides this to interpret the attestation index as the EMPTY/FULL hint,
   * while still leaving the final PENDING/EMPTY/FULL resolution to later forkchoice logic.
   */
  public boolean getFullPayloadVoteHint(final UInt64 attestationIndex) {
    return false;
  }

  /**
   * Determines if the head block is weak (its weight is below the reorg threshold).
   *
   * <p>Spec reference: is_head_weak
   *
   * @param store the fork choice store for accessing weights and fork-aware state
   * @param root the root of the head block
   * @param reorgThreshold the threshold below which a head is considered weak
   * @return true if the head weight is less than the reorg threshold
   */
  public boolean isHeadWeak(
      final ReadOnlyStore store, final Bytes32 root, final UInt64 reorgThreshold) {
    return store
        .getForkChoiceStrategy()
        .getBlockData(root)
        .map(blockData -> blockData.getWeight().isLessThan(reorgThreshold))
        .orElse(false);
  }

  /**
   * Determines if the parent block selected by {@code head} is strong.
   *
   * <p>Spec reference: is_parent_strong
   *
   * <p>Pre-Gloas forks evaluate the parent by root alone. Gloas overrides this form because the
   * spec chooses the parent node identity from the child block's payload linkage rather than from
   * the parent root alone.
   *
   * @param store the fork choice store for accessing weights and fork-aware state
   * @param head the child block whose parent is being evaluated
   * @param parentThreshold the threshold above which a parent is considered strong
   * @return true if the relevant parent node weight is greater than the parent threshold
   */
  public boolean isParentStrong(
      final ReadOnlyStore store, final SignedBeaconBlock head, final UInt64 parentThreshold) {
    return store
        .getForkChoiceStrategy()
        .getBlockData(head.getParentRoot())
        .map(blockData -> blockData.getWeight().isGreaterThan(parentThreshold))
        .orElse(true);
  }

  @VisibleForTesting
  protected int getProposerIndex(final BeaconState proposerPreState) {
    return beaconStateAccessors.getBeaconProposerIndex(proposerPreState);
  }

  public AvailabilityChecker<?> createAvailabilityCheckerOnBlock(final SignedBeaconBlock block) {
    return AvailabilityChecker.NOOP;
  }

  public AvailabilityChecker<?> createAvailabilityCheckerOnExecutionPayloadEnvelope(
      final SignedBeaconBlock block, final SignedExecutionPayloadEnvelope signedEnvelope) {
    return AvailabilityChecker.NOOP;
  }

  public boolean isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope() {
    return false;
  }

  // Used for computing committee indices when producing attestations.
  public int computeCommitteeIndexForAttestation(
      final UInt64 slot,
      final BeaconBlock block,
      final int committeeIndex,
      final ReadOnlyStore store) {
    return committeeIndex;
  }

  public boolean shouldNotifyForkChoiceUpdatedOnBlock() {
    return true;
  }

  public boolean shouldApplyProposerBoost(
      final Bytes32 proposerBoostRoot,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final UInt64 reorgThreshold,
      final BeaconState justifiedState) {
    return true;
  }

  public Optional<ForkChoiceUtilDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  public Optional<ForkChoiceUtilFulu> toVersionFulu() {
    return Optional.empty();
  }

  public Optional<ForkChoiceUtilGloas> toVersionGloas() {
    return Optional.empty();
  }

  public record BlockTimeliness(boolean isTimelyAttestation, boolean isTimelyPtc) {}
}
