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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import com.google.errorprone.annotations.FormatMethod;
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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerPreferencesGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  // Two mainnet epochs for the current lookahead, with 4x overhead for dependent-root reorgs
  private static final int RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE = 256;

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final RecentChainData recentChainData;

  private final Set<ProposerPreferencesDedupKey> seenProposerPreferences =
      LimitedSet.createSynchronizedLRU(RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE);

  public ProposerPreferencesGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final RecentChainData recentChainData) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.signingRootUtil = new SigningRootUtil(spec);
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedProposerPreferences signedProposerPreferences) {
    final ProposerPreferences proposerPreferences = signedProposerPreferences.getMessage();
    final UInt64 proposalSlot = proposerPreferences.getProposalSlot();
    final Bytes32 dependentRoot = proposerPreferences.getDependentRoot();

    /*
     * The proposer lookahead for proposal_slot is fixed as of the start of
     * compute_epoch_at_slot(proposal_slot) - MIN_SEED_LOOKAHEAD, so that epoch and its start slot
     * anchor the remaining rules.
     */
    final int minSeedLookahead = spec.atSlot(proposalSlot).getConfig().getMinSeedLookahead();
    final UInt64 lookaheadEpoch =
        spec.computeEpochAtSlot(proposalSlot).minusMinZero(minSeedLookahead);
    final UInt64 lookaheadEpochStartSlot = spec.computeStartSlotAtEpoch(lookaheadEpoch);

    /*
     * [IGNORE] The proposal slot has not started yet.
     */
    if (gossipValidationHelper.hasSlotStarted(proposalSlot)) {
      return completedFuture(
          ignorePreferences(proposerPreferences, "proposal slot has already started"));
    }

    /*
     * [IGNORE] The proposer for the proposal slot is known -- i.e. the lookahead epoch has started,
     * so its proposer lookahead can be computed.
     */
    if (gossipValidationHelper.isSlotFromFuture(lookaheadEpochStartSlot)) {
      return completedFuture(
          ignorePreferences(
              proposerPreferences, "proposer for the proposal slot is not yet known"));
    }

    /*
     * [IGNORE] The block with root preferences.dependent_root has been seen
     * (a client MAY queue preferences for processing once the block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(dependentRoot)) {
      return completedFuture(
          savePreferencesForFuture(
              proposerPreferences,
              "dependent root has not been seen; saving for future processing"));
    }

    /*
     * [IGNORE] The signed_proposer_preferences is the first valid message for the tuple
     * (preferences.proposal_slot, preferences.dependent_root). The validator index is deliberately
     * excluded: only one validator can be the proposer for that pair, so keying on it as well would
     * let a peer force full validation of arbitrarily many messages for the same slot.
     */
    final ProposerPreferencesDedupKey dedupKey =
        new ProposerPreferencesDedupKey(proposalSlot, dependentRoot);
    if (seenProposerPreferences.contains(dedupKey)) {
      return completedFuture(ignoreAlreadySeen(proposerPreferences));
    }

    /*
     * [REJECT] The dependent root is before the proposer lookahead epoch. A dependent root at or
     * after that boundary cannot be the latest block preceding the epoch.
     */
    final Optional<UInt64> maybeDependentRootSlot =
        recentChainData.getSlotForBlockRoot(dependentRoot);
    if (maybeDependentRootSlot.isPresent()) {
      final UInt64 dependentRootSlot = maybeDependentRootSlot.get();
      if (!dependentRootSlot.isLessThan(lookaheadEpochStartSlot)) {
        return completedFuture(
            rejectPreferences(
                proposerPreferences,
                "dependent root is at slot %s but must be before the proposer lookahead epoch start slot %s",
                dependentRootSlot,
                lookaheadEpochStartSlot));
      }
    }

    /*
     * [IGNORE] The dependent root is a possible dependent block for the lookahead epoch. Without
     * this the checkpoint state below would be built by skipping slots that already contain blocks,
     * yielding a proposer lookahead that no branch actually agrees with.
     */
    if (!gossipValidationHelper.isPossibleDependentRoot(dependentRoot, lookaheadEpochStartSlot)) {
      return completedFuture(
          ignorePreferences(
              proposerPreferences, "dependent root is not a possible dependent block"));
    }

    return recentChainData
        .retrieveCheckpointState(new Checkpoint(lookaheadEpoch, dependentRoot))
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return savePreferencesForFuture(
                    proposerPreferences,
                    "checkpoint state for lookahead epoch %s is unavailable; saving for future processing",
                    lookaheadEpoch);
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] The validator is the proposer for the given slot in the proposer lookahead
               * of the checkpoint state at (lookahead_epoch, preferences.dependent_root).
               */
              final int lookaheadIndex =
                  proposalSlot.minusMinZero(lookaheadEpochStartSlot).intValue();
              final UInt64 expectedValidatorIndex =
                  BeaconStateFulu.required(state).getProposerLookahead().getElement(lookaheadIndex);
              if (!expectedValidatorIndex.equals(proposerPreferences.getValidatorIndex())) {
                return rejectPreferences(
                    proposerPreferences,
                    "validator index does not match expected proposer %s",
                    expectedValidatorIndex);
              }

              /*
               * [REJECT] signed_proposer_preferences.signature is valid with respect to
               * the validator's public key
               */
              if (!isSignatureValid(signedProposerPreferences, state)) {
                return rejectPreferences(proposerPreferences, "invalid signature");
              }

              if (!seenProposerPreferences.add(dedupKey)) {
                return ignoreAlreadySeen(proposerPreferences);
              }

              return acceptPreferences(proposerPreferences);
            })
        .exceptionally(
            error -> {
              return rejectPreferencesWithError(
                  proposerPreferences,
                  error,
                  "unable to generate checkpoint state for lookahead epoch %s",
                  lookaheadEpoch);
            });
  }

  private InternalValidationResult acceptPreferences(
      final ProposerPreferences proposerPreferences) {
    LOG.trace(
        "ProposerPreferences Gossip Validation Result: ACCEPT, context: {}",
        formatProposerPreferencesContext(proposerPreferences));
    return ACCEPT;
  }

  @FormatMethod
  private InternalValidationResult rejectPreferences(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: REJECT, reason: {}", message);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult rejectPreferencesWithError(
      final ProposerPreferences proposerPreferences,
      final Throwable error,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: REJECT, reason: {}", message, error);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult ignorePreferences(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: IGNORE, reason: {}", message);
    return ignore("%s", message);
  }

  @FormatMethod
  private InternalValidationResult savePreferencesForFuture(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: SAVE_FOR_FUTURE, reason: {}", message);
    return saveForFuture("%s", message);
  }

  @FormatMethod
  private String formatProposerPreferencesMessage(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return String.format(
        "%s: %s",
        formatProposerPreferencesContext(proposerPreferences),
        String.format(descriptionTemplate, args));
  }

  private String formatProposerPreferencesContext(final ProposerPreferences proposerPreferences) {
    return String.format(
        "Proposer preferences (validator index %s, proposal slot %s, dependent root %s)",
        proposerPreferences.getValidatorIndex(),
        proposerPreferences.getProposalSlot(),
        proposerPreferences.getDependentRoot());
  }

  private boolean isSignatureValid(
      final SignedProposerPreferences signedProposerPreferences, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignProposerPreferences(
            signedProposerPreferences.getMessage(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        signedProposerPreferences.getMessage().getValidatorIndex(),
        signedProposerPreferences.getSignature(),
        state);
  }

  private InternalValidationResult ignoreAlreadySeen(
      final ProposerPreferences proposerPreferences) {
    return ignorePreferences(proposerPreferences, "already received");
  }

  private record ProposerPreferencesDedupKey(UInt64 proposalSlot, Bytes32 dependentRoot) {}
}
