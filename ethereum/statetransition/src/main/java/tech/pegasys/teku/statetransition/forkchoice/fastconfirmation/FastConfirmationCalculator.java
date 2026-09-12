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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteSnapshot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

/**
 * Executes the Fast Confirmation Rule against a fixed slot-start snapshot: the fork-choice {@code
 * store}, the head selected at slot start, and the current slot. Instances are short-lived and
 * single-threaded (created per slot on the fast confirmation runner).
 *
 * <p>It reads block metadata and ancestry from the protoarray fork-choice strategy and derives the
 * source-state helpers from the {@link FastConfirmationStates} loaded for the slot. Ancestry checks
 * delegate to the fork-choice {@code is_ancestor} via {@link ForkChoiceUtil}, so Gloas
 * payload-status semantics are preserved. Candidate block roots are lifted through {@code
 * get_node_for_root}, while latest messages are resolved through {@code get_supported_node}.
 */
class FastConfirmationCalculator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ForkChoiceUtil forkChoiceUtil;

  private final FastConfirmationStore fcrStore;

  private final ReadOnlyStore store;
  private final ReadOnlyForkChoiceStrategy forkChoice;
  private final VoteSnapshot votes;
  private final FastConfirmationStates states;
  private final Bytes32 head;
  private final UInt64 currentSlot;
  private final UInt64 currentEpoch;

  // Per-instance (per-slot) memoization. The safety-threshold helpers query the same slot
  // committees and slot ranges for every block they score (and re-evaluate a block across the two
  // walk phases), the justifiability gates recompute the current target score and honest FFG
  // support, and the equivocator set is fixed for the slot's vote snapshot; single-threaded per
  // slot, so plain fields suffice. The per-balance-source maps are identity-keyed: a calculator
  // sees at most two source states, and value equality on BeaconState would hash the whole state.
  private final Map<UInt64, IntSet> slotCommitteeBySlot = new HashMap<>();
  private final Map<SlotRange, IntSet> committeeByRange = new HashMap<>();
  private final IdentityHashMap<BeaconState, Map<SlotRange, UInt64>> adversarialWeightBySource =
      new IdentityHashMap<>();
  private final IdentityHashMap<BeaconState, Map<Bytes32, UInt64>> safetyThresholdBySource =
      new IdentityHashMap<>();
  private IntList equivocatingValidatorIndices;
  private UInt64 currentTargetScore;
  private UInt64 honestFfgSupportForCurrentTarget;

  FastConfirmationCalculator(
      final Spec spec,
      final FastConfirmationStore fcrStore,
      final FastConfirmationStates states,
      final UInt64 currentSlot) {
    this.spec = spec;
    this.forkChoiceUtil = spec.atSlot(currentSlot).getForkChoiceUtil();
    this.fcrStore = fcrStore;
    this.store = fcrStore.store();
    this.forkChoice = store.getForkChoiceStrategy();
    this.votes = store.getVoteSnapshot();
    this.states = states;
    // After update_fast_confirmation_variables, current_slot_head == get_head(store).root.
    this.head = fcrStore.currentSlotHead();
    this.currentSlot = currentSlot;
    this.currentEpoch = spec.computeEpochAtSlot(currentSlot);
  }

  /** Implements {@code get_block_slot}. */
  UInt64 getBlockSlot(final Bytes32 blockRoot) {
    return forkChoice
        .blockSlot(blockRoot)
        .orElseThrow(() -> new IllegalStateException("Missing block slot for " + blockRoot));
  }

  /** Implements {@code get_block_epoch}. */
  UInt64 getBlockEpoch(final Bytes32 blockRoot) {
    return spec.computeEpochAtSlot(getBlockSlot(blockRoot));
  }

  Bytes32 getBlockParentRoot(final Bytes32 blockRoot) {
    return forkChoice
        .blockParentRoot(blockRoot)
        .orElseThrow(() -> new IllegalStateException("Missing parent root for " + blockRoot));
  }

  /** Implements {@code store.unrealized_justifications[block_root]}. */
  Checkpoint getUnrealizedJustification(final Bytes32 blockRoot) {
    return getCheckpoints(blockRoot).getUnrealizedJustifiedCheckpoint();
  }

  /** Implements {@code get_checkpoint_for_block}. */
  Checkpoint getCheckpointForBlock(final Bytes32 blockRoot, final UInt64 epoch) {
    return new Checkpoint(epoch, getCheckpointBlock(blockRoot, epoch));
  }

  /** Implements {@code get_current_target}. */
  Checkpoint getCurrentTarget() {
    return getCheckpointForBlock(head, currentEpoch);
  }

  /**
   * Implements {@code get_pulled_up_head_state}: the head state advanced to the start of the
   * current epoch when it lags behind, otherwise the head state as-is. Retrieved by {@link
   * FastConfirmationStateLoader} through the store's checkpoint-state cache, so the epoch
   * transition is computed once and shared with attestation processing instead of replayed here.
   */
  BeaconState getPulledUpHeadState() {
    return states.pulledUpHeadState();
  }

  /**
   * Implements {@code get_slot_committee}: all validators assigned to any committee in {@code
   * slot}.
   *
   * <p>Uses the pulled-up head state — the head state advanced to the start of the current epoch —
   * as the shuffling source rather than the raw head state. All committee queries target slots at
   * or before {@code currentSlot - 1} (epoch {@code <= currentEpoch}), and Teku restricts committee
   * queries to within {@code MIN_SEED_LOOKAHEAD} of the source state's slot ({@code
   * BeaconStateAccessors.validateStateForCommitteeQuery}). A raw head state that lags more than one
   * epoch behind (a long run of empty slots) would therefore throw {@code StateTooOldException} for
   * current-epoch slots and abort confirmation. The intervening slots are empty, so advancing the
   * head state through them is deterministic and yields the same shuffling the real chain would.
   *
   * <p>Memoized per slot: the ranges queried while scoring successive blocks overlap heavily, and
   * the shuffling source is fixed for the whole run.
   */
  IntSet getSlotCommittee(final UInt64 slot) {
    return slotCommitteeBySlot.computeIfAbsent(slot, this::computeSlotCommittee);
  }

  private IntSet computeSlotCommittee(final UInt64 slot) {
    final BeaconState shufflingSource = getPulledUpHeadState();
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final int committeesCount = spec.getCommitteeCountPerSlot(shufflingSource, epoch).intValue();
    final IntSet participants = new IntOpenHashSet();
    for (int committeeIndex = 0; committeeIndex < committeesCount; committeeIndex++) {
      participants.addAll(
          spec.getBeaconCommittee(shufflingSource, slot, UInt64.valueOf(committeeIndex)));
    }
    return participants;
  }

  /**
   * Implements {@code get_attestation_score}: the total effective balance (per {@code
   * balanceSource}) of unslashed, active, non-equivocating validators whose latest vote supports a
   * descendant of {@code nodeRoot}. Latest messages are resolved to fork-choice nodes before
   * checking ancestry, preserving Gloas PENDING, EMPTY, and FULL vote semantics.
   */
  UInt64 getAttestationScore(final Bytes32 nodeRoot, final BeaconState balanceSource) {
    final UInt64 balanceSourceEpoch = spec.getCurrentEpoch(balanceSource);
    final SszList<Validator> validators = balanceSource.getValidators();
    final ForkChoiceNode node = getNodeForRoot(nodeRoot);
    UInt64 score = UInt64.ZERO;
    for (final int index : spec.getActiveValidatorIndices(balanceSource, balanceSourceEpoch)) {
      final Validator validator = validators.get(index);
      if (validator.isSlashed()) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (vote.isEquivocating()) {
        continue;
      }
      final Bytes32 votedRoot = vote.getNextRoot();
      // A zero root means the validator is not in store.latest_messages.
      if (votedRoot.isZero()) {
        continue;
      }

      final Optional<ForkChoiceNode> maybeVotedNode =
          forkChoice.getSupportedNode(
              currentSlot, votedRoot, vote.getNextSlot(), vote.isNextFullPayloadHint());
      if (maybeVotedNode.isEmpty()) {
        continue;
      }

      final ForkChoiceNode votedNode = maybeVotedNode.orElseThrow();
      if (!isAncestor(votedNode, node)) {
        continue;
      }

      score = score.plus(validator.getEffectiveBalance());
    }
    return score;
  }

  /**
   * Computes {@code get_attestation_score} for every block of {@code chainRoots} (blocks of a
   * single chain, ordered oldest first) in one pass over the active validator set, instead of one
   * pass per block.
   *
   * <p>Because the blocks lie on one chain, the blocks a vote supports form a prefix of the list:
   * supporting a descendant of a block implies supporting a descendant of every earlier block.
   * (Ancestry is checked against base/PENDING nodes, exactly as {@link #getAttestationScore} does,
   * and under Gloas a PENDING ancestor matches any payload status, so this is plain block ancestry
   * — transitive along the chain on all forks.) Each vote is therefore resolved once and bucketed
   * at the latest chain block it supports; a block's score is the sum of the buckets from its own
   * position onward.
   *
   * <p>The pass reads the flat zeroed-balances list (see {@link #getScoringBalances}) instead of
   * materializing each {@link Validator}, and resolves each <em>distinct</em> latest message once
   * (most validators vote for the same handful of recent blocks), so the per-validator work is a
   * couple of array reads. The scan itself is memory-bound (a pointer chase across a mainnet-sized
   * vote snapshot), so it is chunked across the common pool — the same careful {@code parallel()}
   * pattern as {@code AbstractValidatorStatusFactory}: all inputs are effectively immutable (the
   * balances list, the vote snapshot, and lock-guarded protoarray reads), each chunk accumulates
   * into private buckets, and the merge runs back on a single thread.
   */
  Map<Bytes32, UInt64> computeChainAttestationScores(
      final List<Bytes32> chainRoots, final BeaconState balanceSource) {
    if (chainRoots.isEmpty()) {
      return Map.of();
    }
    final List<ForkChoiceNode> chainNodes = chainRoots.stream().map(this::getNodeForRoot).toList();
    final List<UInt64> balances = getScoringBalances(balanceSource);

    // Shared across chunks: resolution is deterministic and idempotent, so a racing duplicate
    // computation is harmless.
    final Map<VoteKey, Integer> latestSupportedByVote = new ConcurrentHashMap<>();
    final int chunkCount = Math.min(ForkJoinPool.getCommonPoolParallelism() + 1, 32);
    final int chunkSize = (balances.size() + chunkCount - 1) / chunkCount;
    final List<long[]> chunkSupports =
        IntStream.range(0, chunkCount)
            .parallel()
            .mapToObj(
                chunk ->
                    scanVotesForChainSupport(
                        chainNodes,
                        balances,
                        latestSupportedByVote,
                        chunk * chunkSize,
                        Math.min((chunk + 1) * chunkSize, balances.size())))
            .toList();

    // supportByLatestIndex[i]: total balance of votes whose latest supported chain block is i.
    // Plain longs: the total stake in Gwei is far below 2^63, so the sums cannot overflow.
    final long[] supportByLatestIndex = new long[chainNodes.size()];
    for (final long[] chunkSupport : chunkSupports) {
      for (int i = 0; i < supportByLatestIndex.length; i++) {
        supportByLatestIndex[i] += chunkSupport[i];
      }
    }

    // score(chainRoots[i]) = votes supporting chainRoots[i] or any later chain block.
    final Map<Bytes32, UInt64> scores = HashMap.newHashMap(chainRoots.size());
    long runningScore = 0;
    for (int i = chainRoots.size() - 1; i >= 0; i--) {
      runningScore += supportByLatestIndex[i];
      scores.put(chainRoots.get(i), UInt64.valueOf(runningScore));
    }
    return scores;
  }

  /** One chunk of the scoring scan, over the validator index range {@code [fromIndex, toIndex)}. */
  private long[] scanVotesForChainSupport(
      final List<ForkChoiceNode> chainNodes,
      final List<UInt64> balances,
      final Map<VoteKey, Integer> latestSupportedByVote,
      final int fromIndex,
      final int toIndex) {
    final long[] supportByLatestIndex = new long[chainNodes.size()];
    for (int index = fromIndex; index < toIndex; index++) {
      final UInt64 balance = balances.get(index);
      // Zero balance: inactive or slashed, or contributing no weight anyway.
      if (balance.isZero()) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (vote.isEquivocating()) {
        continue;
      }
      final Bytes32 votedRoot = vote.getNextRoot();
      // A zero root means the validator is not in store.latest_messages.
      if (votedRoot.isZero()) {
        continue;
      }
      final int latestSupported =
          latestSupportedByVote.computeIfAbsent(
              new VoteKey(votedRoot, vote.getNextSlot(), vote.isNextFullPayloadHint()),
              voteKey -> resolveLatestSupportedChainIndex(chainNodes, voteKey));
      if (latestSupported >= 0) {
        supportByLatestIndex[latestSupported] += balance.longValue();
      }
    }
    return supportByLatestIndex;
  }

  /**
   * Resolves the latest message to its supported fork-choice node via {@code get_supported_node},
   * then to the chain prefix boundary; {@code -1} when the vote is unresolvable or supports none.
   */
  private int resolveLatestSupportedChainIndex(
      final List<ForkChoiceNode> chainNodes, final VoteKey voteKey) {
    return forkChoice
        .getSupportedNode(
            currentSlot, voteKey.votedRoot(), voteKey.voteSlot(), voteKey.fullPayloadHint())
        .map(votedNode -> findLatestSupportedChainIndex(chainNodes, votedNode))
        .orElse(-1);
  }

  /**
   * The index of the latest (highest-slot) chain block the vote supports (i.e. of which the voted
   * node is a descendant), or {@code -1} when it supports none. Tests the newest block first — the
   * common case, since most latest messages vote at or near the head and so support the whole chain
   * — and otherwise binary-searches the boundary of the supported prefix.
   */
  private int findLatestSupportedChainIndex(
      final List<ForkChoiceNode> chainNodes, final ForkChoiceNode votedNode) {
    final int last = chainNodes.size() - 1;
    if (isAncestor(votedNode, chainNodes.get(last))) {
      return last;
    }
    int latestSupported = -1;
    int low = 0;
    int high = last - 1;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      if (isAncestor(votedNode, chainNodes.get(mid))) {
        latestSupported = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return latestSupported;
  }

  /**
   * Implements {@code get_block_support_between_slots}: the total effective balance (per {@code
   * balanceSource}) of unslashed, active, non-equivocating validators assigned to the inclusive
   * slot range whose latest vote is exactly {@code blockRoot}.
   */
  UInt64 getBlockSupportBetweenSlots(
      final BeaconState balanceSource,
      final Bytes32 blockRoot,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    final List<UInt64> balances = getScoringBalances(balanceSource);
    UInt64 support = UInt64.ZERO;
    for (final int index : getCommitteeBetweenSlots(startSlot, endSlot)) {
      final UInt64 balance = balances.get(index);
      // Zero balance: inactive or slashed, or contributing no weight anyway.
      if (balance.isZero()) {
        continue;
      }
      final VoteTracker vote = votes.getVote(index);
      if (!vote.isEquivocating() && vote.getNextRoot().equals(blockRoot)) {
        support = support.plus(balance);
      }
    }
    return support;
  }

  /**
   * Implements {@code get_equivocation_score}: the total effective balance (per {@code
   * balanceSource}) of active, equivocating validators assigned to the inclusive slot range. Per
   * spec, slashed validators are not filtered out here (they are very likely already equivocating).
   *
   * <p>Iterates the (almost always empty) equivocator set and checks each member's committee
   * assignment, rather than materializing the committees of the whole slot range and filtering them
   * for equivocators as the spec does. The result is identical — both select the validators that
   * are equivocating and assigned to the range — but no slot committee is computed at all in the
   * common no-equivocation case, which makes {@code compute_adversarial_weight} pure arithmetic.
   */
  UInt64 getEquivocationScore(
      final BeaconState balanceSource, final UInt64 startSlot, final UInt64 endSlot) {
    final IntList equivocatingIndices = getEquivocatingValidatorIndices();
    if (equivocatingIndices.isEmpty()) {
      return UInt64.ZERO;
    }
    final UInt64 balanceSourceEpoch = spec.getCurrentEpoch(balanceSource);
    final SszList<Validator> validators = balanceSource.getValidators();
    UInt64 score = UInt64.ZERO;
    for (final int index : equivocatingIndices) {
      if (!isInCommitteeBetweenSlots(index, startSlot, endSlot)) {
        continue;
      }
      final Validator validator = validators.get(index);
      if (isActiveValidator(validator, balanceSourceEpoch)) {
        score = score.plus(validator.getEffectiveBalance());
      }
    }
    return score;
  }

  /** Indices of validators marked equivocating in the vote snapshot, collected once per slot. */
  private IntList getEquivocatingValidatorIndices() {
    if (equivocatingValidatorIndices == null) {
      final IntArrayList indices = new IntArrayList();
      for (int index = 0; index < votes.size(); index++) {
        if (votes.getVote(index).isEquivocating()) {
          indices.add(index);
        }
      }
      equivocatingValidatorIndices = indices;
    }
    return equivocatingValidatorIndices;
  }

  /** Whether the validator is assigned to any committee of the inclusive slot range. */
  private boolean isInCommitteeBetweenSlots(
      final int validatorIndex, final UInt64 startSlot, final UInt64 endSlot) {
    for (UInt64 slot = startSlot; slot.isLessThanOrEqualTo(endSlot); slot = slot.increment()) {
      if (getSlotCommittee(slot).contains(validatorIndex)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Implements {@code compute_adversarial_weight}: the maximum weight that could be adversarial in
   * the committees of the slot range, assuming {@code CONFIRMATION_BYZANTINE_THRESHOLD} and
   * discounting validators already known to be equivocating.
   *
   * <p>Memoized per (balance source, slot range): the honest-FFG gate re-queries the current
   * epoch's range on every call, and blocks re-evaluated across the walk phases repeat theirs.
   */
  UInt64 computeAdversarialWeight(
      final BeaconState balanceSource, final UInt64 startSlot, final UInt64 endSlot) {
    return adversarialWeightBySource
        .computeIfAbsent(balanceSource, __ -> new HashMap<>())
        .computeIfAbsent(
            new SlotRange(startSlot, endSlot),
            range -> calculateAdversarialWeight(balanceSource, range.startSlot(), range.endSlot()));
  }

  private UInt64 calculateAdversarialWeight(
      final BeaconState balanceSource, final UInt64 startSlot, final UInt64 endSlot) {
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 maximumWeight =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, startSlot, endSlot);
    final UInt64 maxAdversarialWeight =
        maximumWeight
            .dividedBy(100)
            .times(FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    final UInt64 equivocationScore = getEquivocationScore(balanceSource, startSlot, endSlot);
    return maxAdversarialWeight.isGreaterThan(equivocationScore)
        ? maxAdversarialWeight.minus(equivocationScore)
        : UInt64.ZERO;
  }

  /**
   * Implements {@code get_adversarial_weight}: the maximum adversarial weight that can support the
   * block, from the block's first relevant slot up to the slot before the current one.
   */
  UInt64 getAdversarialWeight(final BeaconState balanceSource, final Bytes32 blockRoot) {
    final UInt64 lastSlot = currentSlot.minus(1);
    final UInt64 blockEpoch = getBlockEpoch(blockRoot);
    if (blockEpoch.isGreaterThan(getBlockEpoch(getBlockParentRoot(blockRoot)))) {
      // Use the first epoch slot as the start slot when crossing an epoch boundary.
      return computeAdversarialWeight(
          balanceSource, spec.computeStartSlotAtEpoch(blockEpoch), lastSlot);
    }
    return computeAdversarialWeight(balanceSource, getBlockSlot(blockRoot), lastSlot);
  }

  /**
   * Implements {@code compute_empty_slot_support_discount}: weight discountable from the safety
   * threshold when empty slots precede the block, i.e. parent support from the empty slots'
   * committees beyond what could be adversarial.
   */
  UInt64 computeEmptySlotSupportDiscount(final BeaconState balanceSource, final Bytes32 blockRoot) {
    final Bytes32 parentRoot = getBlockParentRoot(blockRoot);
    final UInt64 blockSlot = getBlockSlot(blockRoot);
    final UInt64 parentSlot = getBlockSlot(parentRoot);
    if (parentSlot.plus(1).equals(blockSlot)) {
      // No empty slot.
      return UInt64.ZERO;
    }
    final UInt64 firstEmptySlot = parentSlot.plus(1);
    final UInt64 lastEmptySlot = blockSlot.minus(1);
    final UInt64 parentSupportInEmptySlots =
        getBlockSupportBetweenSlots(balanceSource, parentRoot, firstEmptySlot, lastEmptySlot);
    final UInt64 adversarialWeight =
        computeAdversarialWeight(balanceSource, firstEmptySlot, lastEmptySlot);
    return parentSupportInEmptySlots.isGreaterThan(adversarialWeight)
        ? parentSupportInEmptySlots.minus(adversarialWeight)
        : UInt64.ZERO;
  }

  /** Implements {@code get_support_discount}. */
  UInt64 getSupportDiscount(final BeaconState balanceSource, final Bytes32 blockRoot) {
    return computeEmptySlotSupportDiscount(balanceSource, blockRoot);
  }

  /**
   * Implements {@code compute_safety_threshold}: the LMD-GHOST safety threshold for the block.
   *
   * <p>Memoized per (balance source, block): the block that stops the previous-epoch walk is
   * re-evaluated by the current-epoch walk, repeating the support-discount and adversarial-weight
   * range work below.
   */
  UInt64 computeSafetyThreshold(final Bytes32 blockRoot, final BeaconState balanceSource) {
    return safetyThresholdBySource
        .computeIfAbsent(balanceSource, __ -> new HashMap<>())
        .computeIfAbsent(blockRoot, root -> calculateSafetyThreshold(root, balanceSource));
  }

  private UInt64 calculateSafetyThreshold(
      final Bytes32 blockRoot, final BeaconState balanceSource) {
    final UInt64 parentSlot = getBlockSlot(getBlockParentRoot(blockRoot));
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 proposerScore = spec.getProposerBoostAmount(balanceSource);
    final UInt64 maximumSupport =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, parentSlot.plus(1), currentSlot.minus(1));
    final UInt64 supportDiscount = getSupportDiscount(balanceSource, blockRoot);
    final UInt64 adversarialWeight = getAdversarialWeight(balanceSource, blockRoot);

    // (maximumSupport + proposerScore + 2 * adversarialWeight - supportDiscount) // 2, guarded
    // against underflow.
    final UInt64 gross = maximumSupport.plus(proposerScore).plus(adversarialWeight.times(2));
    return supportDiscount.isLessThan(gross)
        ? gross.minus(supportDiscount).dividedBy(2)
        : UInt64.ZERO;
  }

  /**
   * Implements {@code is_one_confirmed}: whether the block is LMD-GHOST safe (its support exceeds
   * the safety threshold). Returns {@code false} for a block that is not fully validated (not
   * {@code VALID} per optimistic sync).
   */
  boolean isOneConfirmed(final BeaconState balanceSource, final Bytes32 blockRoot) {
    if (!isValidForConfirmation(blockRoot)) {
      return false;
    }
    return isOneConfirmedWithSupport(
        balanceSource, blockRoot, getAttestationScore(blockRoot, balanceSource));
  }

  /**
   * {@code is_one_confirmed} evaluated against a precomputed attestation score (see {@link
   * #computeChainAttestationScores}); otherwise identical to {@link #isOneConfirmed}.
   */
  private boolean isOneConfirmedWithSupport(
      final BeaconState balanceSource, final Bytes32 blockRoot, final UInt64 support) {
    if (!isValidForConfirmation(blockRoot)) {
      return false;
    }
    final UInt64 safetyThreshold = computeSafetyThreshold(blockRoot, balanceSource);
    return support.isGreaterThan(safetyThreshold);
  }

  /**
   * From the {@code is_one_confirmed} spec: "This function MUST return {@code False} if {@code
   * block_root} status is not {@code VALID} according to the Optimistic sync specification."
   *
   * <p>This maps directly to {@code isFullyValidated} (protoarray {@code VALID}) across all forks,
   * so an optimistically-imported block is never confirmed. Both pre-Gloas and Gloas resolve the
   * block-level (Gloas {@code PENDING}) base node — see {@code get_node_for_root} — and {@code
   * isFullyValidated} additionally requires that node to be {@code VALID}. A weaker presence check
   * ({@code contains}) would confirm nodes that are still {@code OPTIMISTIC}, including a block
   * that inherited optimistic status from an unvalidated execution parent, violating the spec's
   * MUST.
   */
  private boolean isValidForConfirmation(final Bytes32 blockRoot) {
    return forkChoice.isFullyValidated(blockRoot);
  }

  /**
   * Implements {@code get_current_target_score}: the estimated FFG support of the current-epoch
   * target, using the pulled-up head state's validator set and the LMD votes received so far.
   *
   * <p>Memoized: a full pass over the active validator set that the justifiability gates ({@code
   * will_no_conflicting_checkpoint_be_justified}, {@code will_current_target_be_justified}) would
   * otherwise recompute several times per slot against the same snapshot.
   */
  UInt64 getCurrentTargetScore() {
    if (currentTargetScore == null) {
      currentTargetScore = computeCurrentTargetScore();
    }
    return currentTargetScore;
  }

  private UInt64 computeCurrentTargetScore() {
    final Checkpoint target = getCurrentTarget();
    final BeaconState state = getPulledUpHeadState();
    final UInt64 epoch = spec.getCurrentEpoch(state);
    final SszList<Validator> validators = state.getValidators();
    // Deliberately does NOT use the flat zeroed-balances list here: the pulled-up head state
    // changes every slot, so building (and caching) a one-shot per-state list costs far more than
    // it saves. Instead the vote filters run first, and the validator is only read for votes from
    // the target epoch — a small subset on the early-epoch slots where the justifiability gates
    // actually run.
    // Distinct voted roots are few, so each root's checkpoint block is resolved only once.
    final Map<Bytes32, Boolean> supportsTargetByVotedRoot = new HashMap<>();
    final int indexLimit = Math.min(votes.size(), validators.size());
    UInt64 score = UInt64.ZERO;
    for (int index = 0; index < indexLimit; index++) {
      final VoteTracker vote = votes.getVote(index);
      if (vote.isEquivocating()) {
        continue;
      }
      final Bytes32 votedRoot = vote.getNextRoot();
      if (votedRoot.isZero()) {
        continue;
      }
      final UInt64 messageEpoch = spec.computeEpochAtSlot(vote.getNextSlot());
      // A vote can only support the target when its message epoch matches the target epoch, since
      // Checkpoint equality requires equal epochs. Filtering on the epoch first also avoids
      // resolving the vote's checkpoint block: for a voter that has not voted for one or more
      // epochs the message epoch can sit at or below the finalized checkpoint, whose block has
      // been pruned from fork choice (protoarray only retains finalized-onward blocks, while the
      // spec assumes every block stays in store.blocks), and get_checkpoint_block would otherwise
      // throw. Such a vote cannot match the current-epoch target anyway, so it contributes no
      // score.
      if (!messageEpoch.equals(target.getEpoch())) {
        continue;
      }
      if (!forkChoice.contains(votedRoot)) {
        continue;
      }
      // messageEpoch == target epoch here, so the resolution depends on the root alone.
      final boolean supportsTarget =
          supportsTargetByVotedRoot.computeIfAbsent(
              votedRoot, root -> target.equals(getCheckpointForBlock(root, target.getEpoch())));
      if (!supportsTarget) {
        continue;
      }
      final Validator validator = validators.get(index);
      if (validator.isSlashed() || !isActiveValidator(validator, epoch)) {
        continue;
      }
      score = score.plus(validator.getEffectiveBalance());
    }
    return score;
  }

  /**
   * Implements {@code compute_honest_ffg_support_for_current_target}: the minimum honest FFG
   * support the current-epoch target can be assured of, assuming synchrony and {@code
   * CONFIRMATION_BYZANTINE_THRESHOLD}.
   *
   * <p>Memoized: both justifiability gates derive from it and can each run more than once per slot
   * against the same snapshot.
   */
  UInt64 computeHonestFfgSupportForCurrentTarget() {
    if (honestFfgSupportForCurrentTarget == null) {
      honestFfgSupportForCurrentTarget = calculateHonestFfgSupportForCurrentTarget();
    }
    return honestFfgSupportForCurrentTarget;
  }

  private UInt64 calculateHonestFfgSupportForCurrentTarget() {
    final BeaconState balanceSource = getPulledUpHeadState();
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(balanceSource);
    final UInt64 ffgSupportForCheckpoint = getCurrentTargetScore();
    final UInt64 epochStart = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 lastSlot = currentSlot.minus(1);

    // Total FFG weight already assigned up to, but excluding, the current slot.
    final UInt64 ffgWeightTillNow =
        FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
            spec, totalActiveBalance, epochStart, lastSlot);
    final UInt64 remainingFfgWeight = totalActiveBalance.minusMinZero(ffgWeightTillNow);
    final UInt64 remainingHonestFfgWeight =
        remainingFfgWeight
            .dividedBy(100)
            .times(100 - FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);

    final UInt64 adversarialWeight = computeAdversarialWeight(balanceSource, epochStart, lastSlot);
    // ffg_support - min(adversarial_weight, ffg_support)
    final UInt64 minHonestFfgSupport = ffgSupportForCheckpoint.minusMinZero(adversarialWeight);

    return minHonestFfgSupport.plus(remainingHonestFfgWeight);
  }

  /**
   * Implements {@code will_no_conflicting_checkpoint_be_justified}: whether no checkpoint
   * conflicting with the current target can ever be justified.
   */
  boolean willNoConflictingCheckpointBeJustified() {
    // If the target is already the greatest unrealized justified checkpoint, nothing can conflict.
    if (getCurrentTarget().equals(getStoreUnrealizedJustifiedCheckpoint())) {
      return true;
    }
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(getPulledUpHeadState());
    return computeHonestFfgSupportForCurrentTarget().times(3).isGreaterThan(totalActiveBalance);
  }

  /**
   * Implements {@code will_current_target_be_justified}: whether the current target will eventually
   * be justified.
   */
  boolean willCurrentTargetBeJustified() {
    final UInt64 totalActiveBalance = spec.getTotalActiveBalance(getPulledUpHeadState());
    return computeHonestFfgSupportForCurrentTarget()
        .times(3)
        .isGreaterThanOrEqualTo(totalActiveBalance.times(2));
  }

  /**
   * Implements {@code is_confirmed_chain_safe}: whether every block of the confirmed chain, from
   * the current-epoch observed justified checkpoint up to {@code confirmedRoot}, is LMD-GHOST safe
   * against the previous balance source. Run at the start of each epoch to relax the synchrony
   * assumption (reconfirmation).
   */
  boolean isConfirmedChainSafe(final Bytes32 confirmedRoot) {
    final Checkpoint observedJustified = fcrStore.currentEpochObservedJustifiedCheckpoint();
    // The observed justified checkpoint must be on the confirmed chain.
    if (!observedJustified.equals(
        getCheckpointForBlock(confirmedRoot, observedJustified.getEpoch()))) {
      return false;
    }

    final Bytes32 startRootExclusive;
    if (observedJustified.getEpoch().plus(1).isGreaterThanOrEqualTo(currentEpoch)) {
      // Exclude the justified checkpoint block: from the previous epoch it is always canonical.
      startRootExclusive = observedJustified.getRoot();
    } else {
      // Limit reconfirmation to the first block of the previous epoch; confirming it implies its
      // ancestors.
      final Bytes32 ancestorAtPreviousEpochStart =
          getAncestorRoot(confirmedRoot, spec.computeStartSlotAtEpoch(currentEpoch.minus(1)));
      startRootExclusive =
          getBlockEpoch(ancestorAtPreviousEpochStart).plus(1).equals(currentEpoch)
              ? getBlockParentRoot(ancestorAtPreviousEpochStart)
              : ancestorAtPreviousEpochStart;
    }

    final BeaconState previousBalanceSource =
        states
            .previousBalanceSource()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Previous balance source is required for reconfirmation"));
    final List<Bytes32> chainToReconfirm = getAncestorRoots(confirmedRoot, startRootExclusive);
    // Score the whole chain in a single pass over the validator set instead of one per block.
    final Map<Bytes32, UInt64> chainScores =
        computeChainAttestationScores(chainToReconfirm, previousBalanceSource);
    return chainToReconfirm.stream()
        .allMatch(
            root -> isOneConfirmedWithSupport(previousBalanceSource, root, chainScores.get(root)));
  }

  /**
   * Implements {@code find_latest_confirmed_descendant}: the most recent confirmed block in the
   * canonical suffix starting from {@code latestConfirmedRoot}. It first tries to advance across
   * previous-epoch blocks (relying on the previous slot head), then further into the current epoch
   * (gated on the current target being justifiable), keeping the result only if it cannot be
   * reorged out in the current or next epoch.
   */
  Bytes32 findLatestConfirmedDescendant(final Bytes32 latestConfirmedRoot) {
    final boolean atEpochStart = FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, currentSlot);
    final Bytes32 previousSlotHead = fcrStore.previousSlotHead();
    final BeaconState currentBalanceSource = states.currentBalanceSource();
    Bytes32 confirmedRoot = latestConfirmedRoot;

    // Both walk phases score blocks from this one chain (the second phase from a suffix of it), so
    // their attestation scores are computed together in a single pass over the validator set on
    // first need (see computeChainAttestationScores) instead of one pass per block. Left null until
    // a phase actually scores, so slots where neither gate passes do no scoring work at all.
    final List<Bytes32> candidateChain = getAncestorRoots(head, latestConfirmedRoot);
    Map<Bytes32, UInt64> chainScores = null;

    // The previous slot head is a root persisted in the FCR store across slots, so it may have been
    // pruned from fork choice (protoarray only keeps finalized-onward blocks, while the spec
    // assumes
    // every block stays in store.blocks). When it is gone we cannot rely on it, so skip the
    // previous-epoch advance rather than dereferencing it — mirrors the guard get_latest_confirmed
    // applies to its other persisted roots.
    if (forkChoice.contains(previousSlotHead)
        && getBlockEpoch(confirmedRoot).plus(1).equals(currentEpoch)
        && epochPlusIsAtLeastCurrent(getVotingSource(previousSlotHead).getEpoch(), 2)
        && (atEpochStart
            || (willNoConflictingCheckpointBeJustified()
                && (epochPlusIsAtLeastCurrent(
                        getUnrealizedJustification(previousSlotHead).getEpoch(), 1)
                    || epochPlusIsAtLeastCurrent(
                        getUnrealizedJustification(head).getEpoch(), 1))))) {
      chainScores = computeChainAttestationScores(candidateChain, currentBalanceSource);
      // Advance towards the head over previous-epoch blocks; stop at the first unconfirmed one.
      for (final Bytes32 blockRoot : candidateChain) {
        // Only meant to confirm previous-epoch blocks.
        if (getBlockEpoch(blockRoot).equals(currentEpoch)) {
          break;
        }
        // Can only rely on the previous head if it descends from the block being confirmed.
        if (!isAncestor(previousSlotHead, blockRoot)) {
          break;
        }
        if (!isOneConfirmedWithSupport(
            currentBalanceSource, blockRoot, chainScores.get(blockRoot))) {
          break;
        }
        confirmedRoot = blockRoot;
      }
    }

    if (atEpochStart || epochPlusIsAtLeastCurrent(getUnrealizedJustification(head).getEpoch(), 1)) {
      if (chainScores == null) {
        chainScores = computeChainAttestationScores(candidateChain, currentBalanceSource);
      }
      Bytes32 tentativeConfirmedRoot = confirmedRoot;
      // A suffix of candidateChain: confirmedRoot only ever advances along it, so every walked
      // block already has a precomputed score.
      for (final Bytes32 blockRoot : getAncestorRoots(head, confirmedRoot)) {
        // Only true the first time the walk advances into the current epoch.
        if (getBlockEpoch(blockRoot).isGreaterThan(getBlockEpoch(tentativeConfirmedRoot))
            && !willCurrentTargetBeJustified()) {
          break;
        }
        if (!isOneConfirmedWithSupport(
            currentBalanceSource, blockRoot, chainScores.get(blockRoot))) {
          break;
        }
        tentativeConfirmedRoot = blockRoot;
      }

      // Only keep the advance if the block cannot be reorged out this epoch or the next.
      if (getBlockEpoch(tentativeConfirmedRoot).equals(currentEpoch)
          || (epochPlusIsAtLeastCurrent(getVotingSource(tentativeConfirmedRoot).getEpoch(), 2)
              && (atEpochStart || willNoConflictingCheckpointBeJustified()))) {
        confirmedRoot = tentativeConfirmedRoot;
      }
    }

    return confirmedRoot;
  }

  /**
   * Implements {@code get_latest_confirmed}: the FCR entry point. Reverts {@code confirmed_root} to
   * the finalized block when it is too old, no longer canonical, or (at an epoch boundary) fails
   * reconfirmation; optionally restarts the chain from the observed justified checkpoint; then
   * advances via {@link #findLatestConfirmedDescendant}.
   */
  Bytes32 getLatestConfirmed() {
    final boolean atEpochStart = FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, currentSlot);
    Bytes32 confirmedRoot = fcrStore.confirmedRoot();

    // Revert to the finalized block if the confirmed block is no longer tracked by fork choice
    // (pruned below the finalized anchor as finality advanced — the spec assumes every block stays
    // in store.blocks, but Teku's protoarray only keeps finalized-onward), is more than one epoch
    // old, no longer an ancestor of the head, or (at an epoch boundary) its chain can no longer be
    // reconfirmed. The fork-choice-presence check is first so the epoch/ancestry lookups below
    // never
    // run on a pruned root.
    if (!forkChoice.contains(confirmedRoot)
        || getBlockEpoch(confirmedRoot).plus(1).isLessThan(currentEpoch)
        || !isAncestor(head, confirmedRoot)
        || (atEpochStart && !isConfirmedChainSafe(confirmedRoot))) {
      confirmedRoot = store.getFinalizedCheckpoint().getRoot();
    }

    // Restart the confirmation chain from the observed justified checkpoint when, at an epoch
    // boundary, that checkpoint is from the previous epoch, equals the head's unrealized
    // justification, and the confirmed block is older than it. Skip when the checkpoint block has
    // been pruned from fork choice.
    final Checkpoint observedJustified = fcrStore.currentEpochObservedJustifiedCheckpoint();
    if (atEpochStart && forkChoice.contains(observedJustified.getRoot())) {
      final UInt64 observedJustifiedBlockSlot = getBlockSlot(observedJustified.getRoot());
      if (spec.computeEpochAtSlot(observedJustifiedBlockSlot).plus(1).equals(currentEpoch)
          && observedJustified.equals(getUnrealizedJustification(head))
          && getBlockSlot(confirmedRoot).isLessThan(observedJustifiedBlockSlot)) {
        confirmedRoot = observedJustified.getRoot();
      }
    }

    // Attempt to advance the confirmed block further; only meaningful while it is recent.
    final boolean advanceRan =
        getBlockEpoch(confirmedRoot).plus(1).isGreaterThanOrEqualTo(currentEpoch);
    final Bytes32 result =
        advanceRan ? findLatestConfirmedDescendant(confirmedRoot) : confirmedRoot;

    if (LOG.isTraceEnabled()) {
      final Object headUnrealizedJustifiedEpoch =
          forkChoice.getBlockData(head).isPresent()
              ? getUnrealizedJustification(head).getEpoch()
              : "n/a";
      LOG.trace(
          "FCR getLatestConfirmed slot={} epoch={} atEpochStart={} headFullyValidated={} finalizedEpoch={} observedJustifiedEpoch={} headUnrealizedJustifiedEpoch={} confirmedEpochBeforeAdvance={} advanceRan={} resultEpoch={}",
          currentSlot,
          currentEpoch,
          atEpochStart,
          forkChoice.isFullyValidated(head),
          getBlockEpoch(store.getFinalizedCheckpoint().getRoot()),
          observedJustified.getEpoch(),
          headUnrealizedJustifiedEpoch,
          getBlockEpoch(confirmedRoot),
          advanceRan,
          getBlockEpoch(result));
    }
    return result;
  }

  /**
   * Returns {@code epoch + offset >= currentEpoch} (spec pattern {@code checkpoint.epoch + n >=
   * current_epoch}).
   */
  private boolean epochPlusIsAtLeastCurrent(final UInt64 epoch, final long offset) {
    return epoch.plus(offset).isGreaterThanOrEqualTo(currentEpoch);
  }

  /** Reconstructs {@code store.unrealized_justified_checkpoint} (the store-level greatest). */
  private Checkpoint getStoreUnrealizedJustifiedCheckpoint() {
    return FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store);
  }

  /**
   * Union of {@code get_slot_committee} over the inclusive slot range {@code [startSlot, endSlot]}.
   * Memoized per range, so a re-evaluated block does not rebuild its union.
   */
  private IntSet getCommitteeBetweenSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return committeeByRange.computeIfAbsent(
        new SlotRange(startSlot, endSlot),
        range -> computeCommitteeBetweenSlots(range.startSlot(), range.endSlot()));
  }

  private IntSet computeCommitteeBetweenSlots(final UInt64 startSlot, final UInt64 endSlot) {
    final IntSet participants = new IntOpenHashSet();
    for (UInt64 slot = startSlot; slot.isLessThanOrEqualTo(endSlot); slot = slot.increment()) {
      participants.addAll(getSlotCommittee(slot));
    }
    return participants;
  }

  private boolean isActiveValidator(final Validator validator, final UInt64 epoch) {
    return spec.atEpoch(epoch).predicates().isActiveValidator(validator, epoch);
  }

  /**
   * Per-validator-index effective balances with inactive and slashed validators zeroed — exactly
   * the validators the LMD and FFG scoring passes may count, so a zero entry is skipped without
   * reading the validator. Cached inside the state's transition caches (and shared with protoarray
   * fork-choice scoring), so the list is built at most once per state instead of materializing
   * every {@link Validator} on every pass. Not applicable to {@code get_equivocation_score}, which
   * per spec does not filter out slashed validators.
   */
  private List<UInt64> getScoringBalances(final BeaconState balanceSource) {
    return spec.getBeaconStateUtil(balanceSource.getSlot())
        .getEffectiveActiveUnslashedBalances(balanceSource);
  }

  /**
   * Implements {@code get_voting_source}: the voting source of a block is its unrealized justified
   * checkpoint when the block is from a prior epoch (pulled up), otherwise its realized justified
   * checkpoint. Teku's protoarray stores the block's post-state realized justified checkpoint,
   * which equals the spec's {@code store.block_states[block_root].current_justified_checkpoint}, so
   * no block state needs to be loaded here.
   */
  Checkpoint getVotingSource(final Bytes32 blockRoot) {
    final UInt64 blockEpoch = getBlockEpoch(blockRoot);
    if (currentEpoch.isGreaterThan(blockEpoch)) {
      return getUnrealizedJustification(blockRoot);
    }
    return getCheckpoints(blockRoot).getJustifiedCheckpoint();
  }

  /**
   * Implements {@code is_ancestor(store, get_node_for_root(descendantRoot),
   * get_node_for_root(ancestorRoot))}: {@code true} if {@code ancestorRoot} is an ancestor of
   * {@code descendantRoot} (a block is considered its own ancestor).
   *
   * <p>Delegates to the fork-choice {@code is_ancestor} rather than re-deriving ancestry, so the
   * Gloas payload-status semantics are honoured.
   */
  boolean isAncestor(final Bytes32 descendantRoot, final Bytes32 ancestorRoot) {
    return isAncestor(getNodeForRoot(descendantRoot), getNodeForRoot(ancestorRoot));
  }

  private boolean isAncestor(
      final ForkChoiceNode descendantNode, final ForkChoiceNode ancestorNode) {
    return forkChoiceUtil.isAncestor(forkChoice, descendantNode, ancestorNode);
  }

  /**
   * Implements {@code get_node_for_root}: pre-Gloas {@code ForkChoiceNode(root)}, Gloas {@code
   * ForkChoiceNode(root, PAYLOAD_STATUS_PENDING)}. Both map to the {@linkplain
   * ForkChoiceNode#createBase(Bytes32) base (PENDING)} node.
   */
  private ForkChoiceNode getNodeForRoot(final Bytes32 blockRoot) {
    return ForkChoiceNode.createBase(blockRoot);
  }

  /**
   * Implements {@code get_ancestor_roots}: the ancestors of {@code blockRoot} (inclusive) down to,
   * but excluding, {@code terminalRoot}, ordered oldest first. Returns an empty list when {@code
   * terminalRoot} is not in the chain of {@code blockRoot}.
   */
  List<Bytes32> getAncestorRoots(final Bytes32 blockRoot, final Bytes32 terminalRoot) {
    final Deque<Bytes32> ancestorRoots = new ArrayDeque<>();
    final UInt64 terminalSlot = getBlockSlot(terminalRoot);
    Bytes32 root = blockRoot;
    while (getBlockSlot(root).isGreaterThan(terminalSlot)) {
      ancestorRoots.addFirst(root);
      root = getBlockParentRoot(root);
      // Return when terminalRoot is reached
      if (root.equals(terminalRoot)) {
        return new ArrayList<>(ancestorRoots);
      }
    }
    // Return empty list if terminalRoot is not in the chain of blockRoot
    return List.of();
  }

  /** Implements {@code get_checkpoint_block}. */
  private Bytes32 getCheckpointBlock(final Bytes32 blockRoot, final UInt64 epoch) {
    return getAncestorRoot(blockRoot, spec.computeStartSlotAtEpoch(epoch));
  }

  /** Implements {@code get_ancestor(store, node, slot).root}. */
  private Bytes32 getAncestorRoot(final Bytes32 blockRoot, final UInt64 slot) {
    return forkChoice
        .getAncestor(blockRoot, slot)
        .orElseThrow(
            () ->
                new IllegalStateException("Missing ancestor of " + blockRoot + " at slot " + slot));
  }

  private BlockCheckpoints getCheckpoints(final Bytes32 blockRoot) {
    return forkChoice
        .getBlockData(blockRoot)
        .map(ProtoNodeData::getCheckpoints)
        .orElseThrow(() -> new IllegalStateException("Missing checkpoints for " + blockRoot));
  }

  /** Inclusive slot range used as a memoization key. */
  private record SlotRange(UInt64 startSlot, UInt64 endSlot) {}

  /** A latest-message identity: the inputs {@code get_supported_node} resolves a vote from. */
  private record VoteKey(Bytes32 votedRoot, UInt64 voteSlot, boolean fullPayloadHint) {}
}
