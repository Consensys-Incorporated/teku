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
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.spec.config.Constants.HIGHEST_BID_SET_SIZE;
import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_BUILDERS_BIDS;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import com.google.errorprone.annotations.FormatMethod;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;

public class ExecutionPayloadBidGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final int minBidIncrementPercentage;
  private final Map<UInt64, Set<BuilderAndParent>> seenExecutionPayloadBids =
      LimitedMap.createSynchronizedLRU(MAX_SLOTS_TO_TRACK_BUILDERS_BIDS);
  private final Map<BidParent, UInt64> highestBids =
      LimitedMap.createSynchronizedLRU(HIGHEST_BID_SET_SIZE);

  private final ProposerPreferencesManager proposerPreferencesManager;

  public ExecutionPayloadBidGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ProposerPreferencesManager proposerPreferencesManager,
      final int minBidIncrementPercentage) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.minBidIncrementPercentage = minBidIncrementPercentage;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    final ExecutionPayloadBid bid = signedExecutionPayloadBid.getMessage();

    /*
     * [REJECT] bid.execution_payment is zero.
     */
    final UInt64 executionPayment = bid.getExecutionPayment();
    if (!executionPayment.isZero()) {
      return completedFuture(
          rejectBid(bid, "execution payment should be 0 but was %s", executionPayment));
    }

    /*
     * [REJECT] The bid's block hash is not equal to its parent block hash
     */

    if (bid.getBlockHash().equals(bid.getParentBlockHash())) {
      return completedFuture(rejectBid(bid, "block hash and parent block hash are the same"));
    }

    /*
     * [IGNORE] bid.slot is the current slot or the next slot.
     */
    if (!gossipValidationHelper.isSlotCurrentOrNext(bid.getSlot())) {
      return completedFuture(
          ignoreBid(bid, "must be for current or next slot but was for slot %s", bid.getSlot()));
    }

    /*
     * [REJECT] the number of bid.blob_kzg_commitments is within the limit for the bid's epoch
     * -- i.e. len(bid.blob_kzg_commitments) <= get_blob_parameters(proposal_epoch).max_blobs_per_block.
     */
    final Optional<Integer> maybeMaxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(bid.getSlot());
    if (maybeMaxBlobsPerBlock.isPresent()) {
      final int maxBlobsPerBlock = maybeMaxBlobsPerBlock.get();
      final int blobKzgCommitmentsCount = bid.getBlobKzgCommitments().size();
      if (blobKzgCommitmentsCount > maxBlobsPerBlock) {
        return completedFuture(
            rejectBid(
                bid,
                "has %s blob kzg commitments which exceeds the maximum of %s for the slot",
                blobKzgCommitmentsCount,
                maxBlobsPerBlock));
      }
    }

    /*
     * [IGNORE] the SignedProposerPreferences where preferences.proposal_slot is equal to
     * bid.slot has been seen
     */
    final Optional<ProposerPreferences> proposerPreferences =
        proposerPreferencesManager.getProposerPreferences(bid.getSlot());
    if (proposerPreferences.isEmpty()) {
      return completedFuture(
          saveBidForFuture(bid, "no proposer preferences available; saving for future processing"));
    }

    /*
     * [REJECT] bid.fee_recipient matches the fee_recipient from the proposer's
     * SignedProposerPreferences associated with bid.slot
     */
    if (!bid.getFeeRecipient().equals(proposerPreferences.get().getFeeRecipient())) {
      return completedFuture(
          ignoreBid(
              bid,
              "fee recipient %s does not match proposer preferences fee recipient %s",
              bid.getFeeRecipient(),
              proposerPreferences.get().getFeeRecipient()));
    }

    /*
     * [IGNORE] this is the first signed bid seen with a valid signature from the given builder for the tuple
     * (bid.slot, bid.parent_block_hash, bid.parent_block_root).
     */
    final BuilderAndParent builderAndParent =
        new BuilderAndParent(
            bid.getBuilderIndex(), bid.getParentBlockHash(), bid.getParentBlockRoot());
    if (seenExecutionPayloadBids.getOrDefault(bid.getSlot(), Set.of()).contains(builderAndParent)) {
      return completedFuture(
          ignoreBid(
              bid,
              "already received for parent block hash %s and parent block root %s",
              bid.getParentBlockHash(),
              bid.getParentBlockRoot()));
    }

    /*
     * [IGNORE] this bid is the highest value bid seen for the tuple
     * (bid.slot, bid.parent_block_hash, bid.parent_block_root).
     *
     * Note: Implementations SHOULD include DoS prevention measures to
     * mitigate spam from malicious builders submitting numerous bids with minimal value increments.
     * Possible strategies include: (1) only forwarding bids that exceed the current highest bid by a
     * minimum threshold, or (2) forwarding only the highest observed bid at regular time intervals.
     *
     */
    final BidParent bidValueKey =
        new BidParent(bid.getSlot(), bid.getParentBlockHash(), bid.getParentBlockRoot());
    final UInt64 existingBidValue = highestBids.getOrDefault(bidValueKey, UInt64.ZERO);
    if (!existingBidValue.isZero()) {
      final UInt64 minRequiredBid = calculateMinimumRequiredBid(existingBidValue);

      if (bid.getValue().isLessThan(minRequiredBid)) {
        return completedFuture(
            ignoreBid(
                bid,
                "does not meet minimum increment threshold (%s%%); current highest is %s ETH and minimum required is %s ETH",
                minBidIncrementPercentage,
                gweiToEth(existingBidValue),
                gweiToEth(minRequiredBid)));
      }
    }

    /*
     * [IGNORE] bid.parent_block_hash is the block hash of a known execution payload in fork choice
     * and is_gas_limit_target_compatible(parent_gas_limit, bid.gas_limit, proposer_preferences.target_gas_limit)
     * is True where parent_gas_limit is the gas_limit of that execution payload.
     */
    final Optional<UInt64> maybeParentGasLimit =
        gossipValidationHelper.getGasLimitForExecutionPayload(
            bid.getParentBlockRoot(), bid.getParentBlockHash());
    if (maybeParentGasLimit.isEmpty()) {
      return completedFuture(
          saveBidForFuture(
              bid,
              "parent execution payload gas limit is unavailable for parent block hash %s; saving for future processing",
              bid.getParentBlockHash()));
    }
    final UInt64 parentGasLimit = maybeParentGasLimit.get();
    final UInt64 targetGasLimit = proposerPreferences.get().getTargetGasLimit();
    if (!isGasLimitTargetCompatible(parentGasLimit, bid.getGasLimit(), targetGasLimit)) {
      return completedFuture(
          ignoreBid(
              bid,
              "gas limit %s is not compatible with parent gas limit %s and proposer preferences target gas limit %s",
              bid.getGasLimit(),
              parentGasLimit,
              targetGasLimit));
    }

    /*
     * [IGNORE] The bid is compatible with the current head branch, i.e.
     * is_bid_compatible_with_head(store, bid) returns True.
     */
    if (!gossipValidationHelper.isBidCompatibleWithHead(bid)) {
      return completedFuture(
          ignoreBid(
              bid,
              "is not compatible with the current head branch (parent block hash %s, parent block root %s)",
              bid.getParentBlockHash(),
              bid.getParentBlockRoot()));
    }

    /*
     * Retrieve the bid's parent block slot for the remaining validation rules.
     */
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(bid.getParentBlockRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      return completedFuture(
          saveBidForFuture(
              bid,
              "parent block with root %s is unknown; saving for future processing",
              bid.getParentBlockRoot()));
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The bid is for a higher slot than its parent block.
     */
    if (!bid.getSlot().isGreaterThan(parentBlockSlot)) {
      return completedFuture(
          rejectBid(
              bid,
              "slot %s is not greater than parent block slot %s",
              bid.getSlot(),
              parentBlockSlot));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, bid.getParentBlockRoot(), bid.getSlot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return saveBidForFuture(
                    bid,
                    "state for parent block root %s at slot %s is unavailable; saving for future processing",
                    bid.getParentBlockRoot(),
                    parentBlockSlot);
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] bid.prev_randao is the correct RANDAO mix -- i.e. validate that
               * bid.prev_randao == get_randao_mix(parent_state, get_current_epoch(parent_state)).
               */
              final Bytes32 expectedRandaoMix =
                  gossipValidationHelper.getRandaoMixForCurrentEpoch(state, bid.getSlot());
              if (!bid.getPrevRandao().equals(expectedRandaoMix)) {
                return rejectBid(
                    bid,
                    "prev randao %s does not match expected RANDAO mix %s",
                    bid.getPrevRandao(),
                    expectedRandaoMix);
              }

              /*
               * [REJECT] bid.builder_index is a valid/active builder index -- i.e. is_active_builder(state, bid.builder_index) returns True
               */
              /*
               * [REJECT] bid.builder_index is within range -- i.e.
               * bid.builder_index < len(state.builders). Checked explicitly rather than relying on
               * isActiveBuilder so that the builder lookups below are always in bounds.
               */
              final SszList<Builder> builders = BeaconStateGloas.required(state).getBuilders();
              if (bid.getBuilderIndex().isGreaterThanOrEqualTo(builders.size())) {
                return rejectBid(
                    bid,
                    "builder index %s is out of range for the %s builders in the state",
                    bid.getBuilderIndex(),
                    builders.size());
              }

              if (!gossipValidationHelper.isActiveBuilder(
                  bid.getBuilderIndex(), state, bid.getSlot())) {
                return rejectBid(
                    bid,
                    "builder index %s is not valid, active, and non-slashed",
                    bid.getBuilderIndex());
              }

              /*
               * [REJECT] the builder is a payload builder -- i.e.
               * state.builders[bid.builder_index].version == PAYLOAD_BUILDER_VERSION.
               */
              final int builderVersion =
                  builders.get(bid.getBuilderIndex().intValue()).getVersion();
              if (builderVersion != PAYLOAD_BUILDER_VERSION) {
                return rejectBid(
                    bid,
                    "builder index %s has version %s but only payload builder version %s may bid",
                    bid.getBuilderIndex(),
                    builderVersion,
                    PAYLOAD_BUILDER_VERSION);
              }

              /*
               * [IGNORE] bid.value is less or equal than the builder's excess balance
               * -- i.e. MIN_ACTIVATION_BALANCE + bid.value <= state.balances[bid.builder_index].
               */
              if (!gossipValidationHelper.builderHasEnoughBalanceForBid(
                  bid.getValue(), bid.getBuilderIndex(), state, bid.getSlot())) {
                return ignoreBid(bid, "value exceeds the builder's excess balance");
              }

              /*
               * [REJECT] signed_execution_payload_bid.signature is valid with respect to the bid.builder_index.
               */
              if (!isSignatureValid(signedExecutionPayloadBid, state)) {
                return rejectBid(bid, "invalid execution payload bid signature");
              }

              if (!seenExecutionPayloadBids
                  .computeIfAbsent(bid.getSlot(), __ -> ConcurrentHashMap.newKeySet())
                  .add(builderAndParent)) {
                return ignoreBid(
                    bid,
                    "another bid for parent block hash %s and parent block root %s was processed concurrently",
                    bid.getParentBlockHash(),
                    bid.getParentBlockRoot());
              }

              // Atomically check threshold and update the highest bid
              final UInt64 bidValue = bid.getValue();
              final AtomicReference<UInt64> existingBidRef = new AtomicReference<>();
              final UInt64 actualHighestBid =
                  highestBids.compute(
                      bidValueKey,
                      (key, existingValue) -> {
                        existingBidRef.set(existingValue);
                        return computeNewHighestBid(existingValue, bidValue);
                      });

              // Check if our bid was actually accepted
              if (!wasBidAccepted(bidValue, existingBidRef.get(), actualHighestBid)) {
                final UInt64 minRequired = calculateMinimumRequiredBid(actualHighestBid);
                return ignoreBid(
                    bid,
                    "does not meet minimum increment threshold (%s%%) after a concurrent update; current highest is %s ETH and minimum required is %s ETH",
                    minBidIncrementPercentage,
                    gweiToEth(actualHighestBid),
                    gweiToEth(minRequired));
              }

              return acceptBid(bid);
            });
  }

  private InternalValidationResult acceptBid(final ExecutionPayloadBid bid) {
    LOG.trace(
        "ExecutionPayloadBid Gossip Validation Result: ACCEPT, context: {}", formatBidContext(bid));
    return ACCEPT;
  }

  @FormatMethod
  private InternalValidationResult rejectBid(
      final ExecutionPayloadBid bid, final String descriptionTemplate, final Object... args) {
    final String message = formatBidMessage(bid, descriptionTemplate, args);
    LOG.trace("ExecutionPayloadBid Gossip Validation Result: REJECT, reason: {}", message);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult ignoreBid(
      final ExecutionPayloadBid bid, final String descriptionTemplate, final Object... args) {
    final String message = formatBidMessage(bid, descriptionTemplate, args);
    LOG.trace("ExecutionPayloadBid Gossip Validation Result: IGNORE, reason: {}", message);
    return ignore("%s", message);
  }

  @FormatMethod
  private InternalValidationResult saveBidForFuture(
      final ExecutionPayloadBid bid, final String descriptionTemplate, final Object... args) {
    final String message = formatBidMessage(bid, descriptionTemplate, args);
    LOG.trace("ExecutionPayloadBid Gossip Validation Result: SAVE_FOR_FUTURE, reason: {}", message);
    return saveForFuture("%s", message);
  }

  @FormatMethod
  private String formatBidMessage(
      final ExecutionPayloadBid bid, final String descriptionTemplate, final Object... args) {
    return String.format("%s: %s", formatBidContext(bid), String.format(descriptionTemplate, args));
  }

  private String formatBidContext(final ExecutionPayloadBid bid) {
    return String.format(
        "Execution payload bid (builder index %s, slot %s, value %s ETH)",
        bid.getBuilderIndex(), bid.getSlot(), gweiToEth(bid.getValue()));
  }

  /**
   * Calculates the minimum required bid value based on the current highest bid and the configured
   * minimum increment percentage.
   *
   * @param currentHighestBid the current highest bid value
   * @return the minimum bid value required to exceed the threshold
   */
  private UInt64 calculateMinimumRequiredBid(final UInt64 currentHighestBid) {
    final UInt64 minIncrement = currentHighestBid.times(minBidIncrementPercentage).dividedBy(100);
    return currentHighestBid.plus(minIncrement);
  }

  /**
   * Atomically computes the new highest bid value. This method is used within compute() to
   * atomically check the threshold and update the highest bid.
   *
   * @param existingValue the current highest bid value (may be null for first bid)
   * @param newBidValue the new bid value to evaluate
   * @return the value to store (either existing if rejected, or new if accepted)
   */
  private UInt64 computeNewHighestBid(final UInt64 existingValue, final UInt64 newBidValue) {
    // First bid, accept it
    if (existingValue == null) {
      return newBidValue;
    }

    final UInt64 minRequired = calculateMinimumRequiredBid(existingValue);
    // Below threshold, keep existing
    if (newBidValue.isLessThan(minRequired)) {
      return existingValue;
    }
    // Accept the new bid if higher
    return newBidValue.max(existingValue);
  }

  /**
   * Determines if a bid was actually accepted based on the atomic compute operation result. This
   * method correctly handles the case where bidValue equals existingBid (which would fail the
   * threshold check but pass a naive equality test).
   *
   * @param bidValue the bid value that was submitted
   * @param existingBid the bid value that existed before compute (may be null for first bid)
   * @param actualHighestBid the resulting highest bid value after compute
   * @return true if the bid was accepted, false if rejected due to threshold
   */
  private boolean wasBidAccepted(
      final UInt64 bidValue, final UInt64 existingBid, final UInt64 actualHighestBid) {
    // The bid must be the current highest
    if (!actualHighestBid.equals(bidValue)) {
      return false;
    }

    // First bid is always accepted (no threshold to check)
    if (existingBid == null) {
      return true;
    }

    // New bids must meet the minimum increment threshold
    final UInt64 minRequired = calculateMinimumRequiredBid(existingBid);
    return bidValue.isGreaterThanOrEqualTo(minRequired);
  }

  private boolean isSignatureValid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionPayloadBid(
            signedExecutionPayloadBid.getMessage(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
        signingRoot,
        signedExecutionPayloadBid.getMessage().getBuilderIndex(),
        signedExecutionPayloadBid.getSignature(),
        state);
  }

  static boolean isGasLimitTargetCompatible(
      final UInt64 parentGasLimit, final UInt64 gasLimit, final UInt64 targetGasLimit) {
    final UInt64 maxGasLimitDifference =
        parentGasLimit.dividedBy(1024).max(UInt64.ONE).minus(UInt64.ONE);
    final UInt64 minGasLimit = parentGasLimit.minus(maxGasLimitDifference);
    final UInt64 maxGasLimit = parentGasLimit.plus(maxGasLimitDifference);

    if (targetGasLimit.isGreaterThanOrEqualTo(minGasLimit)
        && targetGasLimit.isLessThanOrEqualTo(maxGasLimit)) {
      return gasLimit.equals(targetGasLimit);
    }
    if (targetGasLimit.isGreaterThan(maxGasLimit)) {
      return gasLimit.equals(maxGasLimit);
    }
    return gasLimit.equals(minGasLimit);
  }

  record BuilderAndParent(UInt64 builderIndex, Bytes32 parentBlockHash, Bytes32 parentBlockRoot) {}

  record BidParent(UInt64 slot, Bytes32 parentBlockHash, Bytes32 parentBlockRoot) {}
}
