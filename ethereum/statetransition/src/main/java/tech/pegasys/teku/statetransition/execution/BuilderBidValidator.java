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

package tech.pegasys.teku.statetransition.execution;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BuilderBidValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ProposerPreferencesManager proposerPreferencesManager;
  private final RecentChainData recentChainData;
  private final GossipValidationHelper gossipValidationHelper;

  public BuilderBidValidator(
      final Spec spec,
      final ProposerPreferencesManager proposerPreferencesManager,
      final RecentChainData recentChainData,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.recentChainData = recentChainData;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /**
   * Validates a bid pulled over the Builder API. Bids arriving on the ePBS gossip topic are
   * validated by {@link ExecutionPayloadBidGossipValidator} instead, so checks that gossip already
   * enforces are repeated here rather than assumed.
   *
   * <p><a
   * href="https://github.com/ethereum/builder-specs/blob/main/specs/gloas/validator.md#validating-a-signedexecutionpayloadbid">Validating
   * a SignedExecutionPayloadBid</a>
   *
   * @param signedBid the signed bid to validate
   * @param state the current beacon state
   * @param parentBlockHash the block hash of the parent the block is being built on
   * @param parentBlockRoot the block root of the parent the block is being built on
   * @param builderEntry the entry whose bid request returned this bid
   * @return true if the bid is valid, false otherwise
   */
  public boolean validateBid(
      final SignedExecutionPayloadBid signedBid,
      final BeaconState state,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final BuilderEntry builderEntry) {
    final ExecutionPayloadBid bid = signedBid.getMessage();
    final UInt64 slot = bid.getSlot();

    /*
     * The bid slot is untrusted input, so it is checked against the (trusted) state slot before it
     * is used to resolve the fork-specific helpers below.
     */
    if (!slot.equals(state.getSlot())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: bid slot {} does not match state slot {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          slot,
          state.getSlot());
      return false;
    }

    final SpecVersion specVersion = spec.atSlot(slot);
    if (!specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: slot {} is in {}, which is before Gloas",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          slot,
          specVersion.getMilestone());
      return false;
    }

    final PredicatesGloas predicates = PredicatesGloas.required(specVersion.predicates());
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(specVersion.beaconStateAccessors());
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);

    if (!predicates.isActiveBuilder(state, bid.getBuilderIndex())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: builder is not active",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    /*
     * The three checks below are not part of validate_bid, but are enforced by
     * process_execution_payload_bid and by gossip validation. Bids coming from the Builder API
     * never go through gossip validation, so check them here rather than discovering the problem
     * when our own block fails to process.
     */
    final Builder builder = stateGloas.getBuilders().get(bid.getBuilderIndex().intValue());
    if (builder.getVersion() != PAYLOAD_BUILDER_VERSION) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: builder has version {} but only payload builder version {} may bid",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          builder.getVersion(),
          PAYLOAD_BUILDER_VERSION);
      return false;
    }

    if (bid.getBlockHash().equals(bid.getParentBlockHash())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: block hash and parent block hash are the same",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    final Optional<Integer> maybeMaxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(slot);
    if (maybeMaxBlobsPerBlock.isPresent()
        && bid.getBlobKzgCommitments().size() > maybeMaxBlobsPerBlock.get()) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: has {} blob kzg commitments which exceeds the maximum of {} for the slot",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getBlobKzgCommitments().size(),
          maybeMaxBlobsPerBlock.get());
      return false;
    }

    /*
     * An empty builder_pubkeys list accepts any builder, but a non-empty one is an allowlist and a
     * bid must be signed by one of its keys. Without this check the entry's trusted
     * max_execution_payment, min_bid and builder_boost_factor would be applied to a bid from any
     * active on-chain builder, not just the one the validator client asked for.
     */
    final SszList<SszPublicKey> allowedBuilderPubkeys = builderEntry.getBuilderPubkeys();
    if (!allowedBuilderPubkeys.isEmpty()
        && allowedBuilderPubkeys.stream()
            .noneMatch(pubkey -> pubkey.getBLSPublicKey().equals(builder.getPublicKey()))) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: pubkey {} is not in its configured builder_pubkeys",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          builder.getPublicKey());
      return false;
    }

    if (!bid.getParentBlockHash().equals(stateGloas.getLatestExecutionPayloadBid().getBlockHash())
        && !bid.getParentBlockHash().equals(stateGloas.getLatestBlockHash())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: parent block hash does not extend a known parent",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    if (!bid.getParentBlockRoot().equals(state.getLatestBlockHeader().hashTreeRoot())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: parent block root mismatch",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    /*
     * The check above is the spec one, which allows a bid to extend either the FULL or the EMPTY
     * variant. After an empty slot those two diverge, so additionally require the bid to extend the
     * parent the block is actually being built on. Otherwise a high value bid on the other variant
     * could win selection and then fail process_execution_payload_bid.
     */
    if (!bid.getParentBlockHash().equals(parentBlockHash)
        || !bid.getParentBlockRoot().equals(parentBlockRoot)) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: bid parent (block hash {}, block root {}) does not match the parent the block is being built on (block hash {}, block root {})",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getParentBlockHash(),
          bid.getParentBlockRoot(),
          parentBlockHash,
          parentBlockRoot);
      return false;
    }

    if (!bid.getPrevRandao()
        .equals(
            beaconStateAccessors.getRandaoMix(
                state, beaconStateAccessors.getCurrentEpoch(state)))) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: prev_randao mismatch",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    /*
     * The spec asserts the fee recipient and the gas limit unconditionally, so a bid cannot be
     * accepted without the preferences to check it against. Missing preferences for our own
     * proposal slot means they were never submitted, in which case accepting the bid would let a
     * builder choose the fee recipient.
     */
    final Optional<Bytes32> maybeDependentRoot =
        gossipValidationHelper.getShufflingDependentRoot(bid.getParentBlockRoot(), slot);
    if (maybeDependentRoot.isEmpty()) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: shuffling dependent root is unavailable for parent block root {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getParentBlockRoot());
      return false;
    }

    final Optional<ProposerPreferences> maybeProposerPreferences =
        proposerPreferencesManager.getProposerPreferences(slot, maybeDependentRoot.get());
    if (maybeProposerPreferences.isEmpty()) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: no proposer preferences available for slot {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          slot);
      return false;
    }
    final ProposerPreferences proposerPreferences = maybeProposerPreferences.get();

    if (!bid.getFeeRecipient().equals(proposerPreferences.getFeeRecipient())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: fee recipient {} does not match proposer preferences fee recipient {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getFeeRecipient(),
          proposerPreferences.getFeeRecipient());
      return false;
    }

    final Optional<UInt64> maybeParentGasLimit =
        recentChainData.getExecutionGasLimitForBlockRootAndHash(
            bid.getParentBlockRoot(), bid.getParentBlockHash());
    if (maybeParentGasLimit.isEmpty()) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: parent execution payload gas limit is unavailable for parent block root {} and block hash {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getParentBlockRoot(),
          bid.getParentBlockHash());
      return false;
    }

    if (!ExecutionPayloadBidGossipValidator.isGasLimitTargetCompatible(
        maybeParentGasLimit.get(), bid.getGasLimit(), proposerPreferences.getTargetGasLimit())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: gas limit {} is not compatible with parent gas limit {} and proposer preferences target gas limit {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getGasLimit(),
          maybeParentGasLimit.get(),
          proposerPreferences.getTargetGasLimit());
      return false;
    }

    /*
     * validate_bid only asserts this when bid.value > 0, but process_execution_payload_bid runs it
     * unconditionally, and can_builder_cover_bid returns false for an underfunded builder even for
     * a zero amount (its balance is below MIN_DEPOSIT_AMOUNT plus pending withdrawals). Checking
     * unconditionally stops an active but underfunded builder from winning selection with a zero
     * value bid, boosted by execution_payment, and then failing block processing.
     */
    if (!beaconStateAccessors.canBuilderCoverBid(state, bid.getBuilderIndex(), bid.getValue())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: builder cannot cover bid value {}",
          builderEntry.getUrl(),
          bid.getBuilderIndex(),
          bid.getValue());
      return false;
    }

    if (!specVersion
        .operationSignatureVerifier()
        .verifyExecutionPayloadBidSignature(
            state, signedBid, specVersion.getConfig().getBLSSignatureVerifier())) {
      LOG.warn(
          "Bid from {} (builder {}) rejected: invalid signature",
          builderEntry.getUrl(),
          bid.getBuilderIndex());
      return false;
    }

    return true;
  }
}
