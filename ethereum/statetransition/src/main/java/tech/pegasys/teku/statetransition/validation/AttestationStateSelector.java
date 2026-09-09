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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationStateSelector {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;

  private final LabelledMetric<Counter> appliedSelectorRule;

  public AttestationStateSelector(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;

    appliedSelectorRule =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "attestation_state_selector_total",
            "Counter of the rules applied successfully to find a state against which to validate a gossipped attestation",
            "rule_applied");
  }

  public SafeFuture<Optional<BeaconState>> getStateToValidate(
      final AttestationData attestationData) {
    final Optional<ChainHead> maybeChainHead = recentChainData.getChainHead();
    if (maybeChainHead.isEmpty()) {
      return completedFuture(Optional.empty());
    }
    final ChainHead chainHead = maybeChainHead.get();

    final Bytes32 targetBlockRoot = attestationData.getBeaconBlockRoot();
    final UInt64 attestationEpoch = attestationData.getTarget().getEpoch();

    // If targetBlockRoot is the current chain head, use the chain head
    if (chainHead.getRoot().equals(targetBlockRoot)) {
      appliedSelectorRule.labels("chain_head_is_target").inc();
      return chainHead
          .getState()
          .thenCompose(state -> resolveStateForAttestation(attestationData, state));
    }

    final UInt64 headEpoch = spec.computeEpochAtSlot(chainHead.getSlot());
    final boolean isWithinHistoricalEpochs =
        attestationEpoch
            .plus(spec.getSpecConfig(headEpoch).getEpochsPerHistoricalVector())
            .isGreaterThan(headEpoch);
    if (isWithinHistoricalEpochs
        && isAncestorOfChainHead(chainHead.getRoot(), targetBlockRoot)
        && hasMatchingShufflingDependentRoot(
            chainHead.getRoot(), targetBlockRoot, attestationEpoch)) {
      appliedSelectorRule.labels("ancestor_of_head").inc();
      return chainHead.getState().thenApply(Optional::of);
    }

    final UInt64 earliestSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(attestationEpoch);
    // If the attestation is within the lookahead period for the finalized state, use that
    // If the target block doesn't descend from finalized the attestation is invalid
    final BeaconState finalizedState = recentChainData.getStore().getLatestFinalized().getState();
    if (finalizedState.getSlot().isGreaterThanOrEqualTo(earliestSlot)) {
      appliedSelectorRule.labels("attestation_within_lookahead").inc();
      return completedFuture(Optional.of(finalizedState));
    }

    final Optional<UInt64> targetBlockSlot = recentChainData.getSlotForBlockRoot(targetBlockRoot);
    if (targetBlockSlot.isEmpty()) {
      // Block became unknown, so ignore it
      appliedSelectorRule.labels("block_became_unknown").inc();
      return completedFuture(Optional.empty());
    }

    // We generally have the chain head states, so attempt to locate which chain head is a
    // descendent and use that
    if (isWithinHistoricalEpochs) {
      // if it's an ancestor of any chain head within historic slots, use that chain head.
      final Optional<BeaconState> maybeChainHeadData =
          recentChainData.getChainHeadsIncludingNonViable().stream()
              .filter(
                  head ->
                      isAncestorOfChainHead(head.getRoot(), targetBlockRoot, targetBlockSlot.get())
                          && hasMatchingShufflingDependentRoot(
                              head.getRoot(), targetBlockRoot, attestationEpoch))
              .findFirst()
              .flatMap(
                  protoNodeData -> {
                    LOG.trace(
                        "Found chain for target {}, chain head {}",
                        () -> attestationData.getTarget().getRoot(),
                        protoNodeData::getStateRoot);
                    return recentChainData
                        .getStore()
                        .getBlockStateIfAvailable(protoNodeData.getRoot());
                  });
      if (maybeChainHeadData.isPresent()) {
        appliedSelectorRule.labels("ancestor_of_fork").inc();
        return completedFuture(maybeChainHeadData);
      }
    }

    // Check if the attestations head block state is already available in cache and usable
    if (targetBlockSlot.get().isGreaterThanOrEqualTo(earliestSlot)) {
      final Optional<BeaconState> maybeState =
          recentChainData.getStore().getBlockStateIfAvailable(targetBlockRoot);
      if (maybeState.isPresent()) {
        LOG.debug(
            "Found state in cache for attestationData target {}, source {}, head {} , slot {}",
            attestationData.getTarget().getRoot(),
            attestationData.getSource().getRoot(),
            attestationData.getBeaconBlockRoot(),
            attestationData.getSlot());
        appliedSelectorRule.labels("state_in_cache").inc();
        return SafeFuture.completedFuture(maybeState);
      }
    }

    if (isJustificationTooOld(targetBlockRoot, targetBlockSlot.get())) {
      // we already justified a more recent slot on all compatible heads
      LOG.debug(
          "Ignored attestation gossip: attestationData target {}, source {}, head {} , slot {}",
          attestationData.getTarget().getRoot(),
          attestationData.getSource().getRoot(),
          attestationData.getBeaconBlockRoot(),
          attestationData.getSlot());
      appliedSelectorRule.labels("justified_more_recent_slot").inc();
      return completedFuture(Optional.empty());
    }

    final Checkpoint requiredCheckpoint;
    if (targetBlockSlot.get().isLessThan(earliestSlot)) {
      // Target block is from before the earliest slot so just roll it forward.
      requiredCheckpoint = new Checkpoint(spec.computeEpochAtSlot(earliestSlot), targetBlockRoot);
    } else {
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
          recentChainData.getForkChoiceStrategy().orElseThrow();
      final Optional<Bytes32> maybeAncestorRoot =
          forkChoiceStrategy.getAncestor(targetBlockRoot, earliestSlot);
      if (maybeAncestorRoot.isEmpty()) {
        // The target block has become unknown or doesn't extend from finalized anymore
        // so we can now ignore it.
        appliedSelectorRule.labels("target_block_now_unknown").inc();
        return completedFuture(Optional.empty());
      }
      requiredCheckpoint =
          new Checkpoint(spec.computeEpochAtSlot(earliestSlot), maybeAncestorRoot.get());
    }
    LOG.trace(
        "Retrieving checkpoint state for attestationData target {}, source {}, head {} , slot {}; required checkpoint block root {}",
        attestationData.getTarget().getRoot(),
        attestationData.getSource().getRoot(),
        attestationData.getBeaconBlockRoot(),
        attestationData.getSlot(),
        requiredCheckpoint.getRoot());
    appliedSelectorRule.labels("retrieve_checkpoint_state").inc();
    return recentChainData.retrieveCheckpointState(requiredCheckpoint);
  }

  private Boolean isAncestorOfChainHead(final Bytes32 headRoot, final Bytes32 blockRoot) {
    return recentChainData
        .getSlotForBlockRoot(blockRoot)
        .map(blockSlot -> isAncestorOfChainHead(headRoot, blockRoot, blockSlot))
        .orElse(false);
  }

  private Boolean isAncestorOfChainHead(
      final Bytes32 headRoot, final Bytes32 blockRoot, final UInt64 blockSlot) {
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow()
        .getAncestor(headRoot, blockSlot)
        .map(canonicalRoot -> canonicalRoot.equals(blockRoot))
        .orElse(false);
  }

  /**
   * Checks that {@code candidateRoot} (a chain head, or a fork's chain head) shares the same
   * shuffling-determining history as {@code targetRoot} for {@code epoch}, before we reuse
   * {@code candidateRoot}'s state to compute {@code targetRoot}'s committees.
   *
   * <p>Structural ancestry alone (i.e. {@code targetRoot} lies on the chain leading to {@code
   * candidateRoot}) is not sufficient: the two branches can still have diverged after {@code
   * targetRoot} but before the block whose RANDAO reveal fixes the shuffling seed for {@code
   * epoch} (the "shuffling dependent root", per the {@code get_shuffling_dependent_root} spec
   * helper). If they diverged before that point, {@code candidateRoot}'s state has a different
   * seed and will produce different committees than {@code targetRoot}'s own branch would, so its
   * state must not be reused.
   */
  private boolean hasMatchingShufflingDependentRoot(
      final Bytes32 candidateRoot, final Bytes32 targetRoot, final UInt64 epoch) {
    final Optional<ReadOnlyForkChoiceStrategy> maybeForkChoiceStrategy =
        recentChainData.getForkChoiceStrategy();
    if (maybeForkChoiceStrategy.isEmpty()) {
      return false;
    }
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = maybeForkChoiceStrategy.get();
    final UInt64 minSeedLookahead = UInt64.valueOf(spec.getSpecConfig(epoch).getMinSeedLookahead());
    if (epoch.isLessThanOrEqualTo(minSeedLookahead)) {
      // The shuffling seed for this epoch is derived from the genesis RANDAO mix on every
      // branch, so there is nothing to diverge on yet.
      return true;
    }
    // The committee shuffling for `epoch` is fixed as of the start of `epoch -
    // MIN_SEED_LOOKAHEAD`, i.e. the dependent root is the ancestor at the slot immediately
    // before that boundary.
    final UInt64 dependentSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch).minusMinZero(1);
    final Optional<Bytes32> candidateDependentRoot =
        forkChoiceStrategy.getAncestor(candidateRoot, dependentSlot);
    final Optional<Bytes32> targetDependentRoot =
        forkChoiceStrategy.getAncestor(targetRoot, dependentSlot);
    return candidateDependentRoot.isPresent() && candidateDependentRoot.equals(targetDependentRoot);
  }

  private Boolean isJustifiedCheckpointOfHeadOlderOrEqualToAttestationJustifiedSlot(
      final ProtoNodeData head, final UInt64 justifiedBlockSlot) {
    final Checkpoint justifiedCheckpoint = head.getCheckpoints().getJustifiedCheckpoint();
    final Optional<UInt64> maybeHeadJustifiedSlot =
        recentChainData.getSlotForBlockRoot(justifiedCheckpoint.getRoot());
    return maybeHeadJustifiedSlot
        .map(slot -> slot.isLessThanOrEqualTo(justifiedBlockSlot))
        .orElse(false);
  }

  private boolean isJustificationTooOld(
      final Bytes32 justifiedRoot, final UInt64 justifiedBlockSlot) {

    return recentChainData.getChainHeadsIncludingNonViable().stream()
        // must be attesting to a viable chain
        .filter(head -> isAncestorOfChainHead(head.getRoot(), justifiedRoot, justifiedBlockSlot))
        // must be attesting to something that progresses justification
        .filter(
            head ->
                isJustifiedCheckpointOfHeadOlderOrEqualToAttestationJustifiedSlot(
                    head, justifiedBlockSlot))
        .findFirst()
        .isEmpty();
  }

  /**
   * Committee information is only guaranteed to be stable up to 1 epoch ahead, if block attested to
   * is too old, we need to roll the corresponding state forward to process the attestation
   *
   * @param attestationData The attestation data to be processed
   * @param blockState The state corresponding to the block being attested to
   * @return The state to use for validation of this attestation
   */
  private SafeFuture<Optional<BeaconState>> resolveStateForAttestation(
      final AttestationData attestationData, final BeaconState blockState) {
    final Bytes32 blockRoot = attestationData.getBeaconBlockRoot();
    final Checkpoint targetEpoch = attestationData.getTarget();
    final UInt64 earliestSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(targetEpoch.getEpoch());
    final UInt64 earliestEpoch = spec.computeEpochAtSlot(earliestSlot);
    if (blockState.getSlot().isLessThan(earliestSlot)) {
      final Checkpoint checkpoint = new Checkpoint(earliestEpoch, blockRoot);
      return recentChainData.getStore().retrieveCheckpointState(checkpoint, blockState);
    } else {
      return completedFuture(Optional.of(blockState));
    }
  }
}
