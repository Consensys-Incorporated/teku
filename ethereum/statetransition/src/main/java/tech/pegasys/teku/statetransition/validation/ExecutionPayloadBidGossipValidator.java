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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;
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
     * [IGNORE] This is the first bid for this slot, parent, and builder
     */
    final BuilderAndParent builderAndParent =
        new BuilderAndParent(
            bid.getBuilderIndex(), bid.getParentBlockHash(), bid.getParentBlockRoot());
    if (seenExecutionPayloadBids.getOrDefault(bid.getSlot(), Set.of()).contains(builderAndParent)) {
      return completedFuture(
          ignoreBid(
              bid,
              "already received valid bid for slot %s, parent block hash %s, parent block root %s, and builder index %s",
              bid.getSlot(),
              bid.getParentBlockHash(),
              bid.getParentBlockRoot(),
              bid.getBuilderIndex()));
    }

    /*
     * [IGNORE] This is the highest value bid seen for the slot and parent
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
     * [IGNORE] The bid's slot is the current slot or the next slot
     */
    if (!gossipValidationHelper.isSlotCurrentOrNext(bid.getSlot())) {
      return completedFuture(
          ignoreBid(bid, "must be for current or next slot but was for slot %s", bid.getSlot()));
    }

    /*
     * [REJECT] The bid's execution payment is zero
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
     * [REJECT] The bid's blob KZG commitment count is within the per-epoch limit
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
     * [IGNORE] The bid's parent block root is a known beacon block
     * (MAY be queued until parent is imported)
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
     * [REJECT] The bid is for a higher slot than its parent block
     */
    if (!bid.getSlot().isGreaterThan(parentBlockSlot)) {
      return completedFuture(
          rejectBid(
              bid,
              "slot %s is not greater than parent block slot %s",
              bid.getSlot(),
              parentBlockSlot));
    }

    /*
     * [IGNORE] The bid's slot is within the parent's proposer lookahead
     */
    if (!gossipValidationHelper.isWithinParentProposerLookahead(bid.getSlot(), parentBlockSlot)) {
      return completedFuture(ignoreBid(bid, "bid's slot is past the parent's proposer lookahead"));
    }

    /*
     * [IGNORE] The matching proposer preferences have been seen
     */
    final Optional<Bytes32> maybeDependentRoot =
        gossipValidationHelper.getShufflingDependentRoot(bid.getParentBlockRoot(), bid.getSlot());
    if (maybeDependentRoot.isEmpty()) {
      return completedFuture(
          saveBidForFuture(
              bid, "shuffling dependent root is unavailable; saving for future processing"));
    }
    final Optional<ProposerPreferences> proposerPreferences =
        proposerPreferencesManager.getProposerPreferences(bid.getSlot(), maybeDependentRoot.get());
    if (proposerPreferences.isEmpty()) {
      return completedFuture(
          saveBidForFuture(bid, "no proposer preferences available; saving for future processing"));
    }

    /*
     * [IGNORE] The bid's fee recipient matches the proposer's preference
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
     * [IGNORE] The bid's parent block hash is the hash of a known execution payload
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

    /*
     * [IGNORE] The bid's gas limit is compatible with the proposer's target gas limit
     */
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
     * [IGNORE] The bid is compatible with the current head branch
     */
    if (!gossipValidationHelper.isBidCompatibleWithHead(bid)) {
      return completedFuture(
          ignoreBid(
              bid,
              "is not compatible with the current head branch (parent block hash %s, parent block root %s)",
              bid.getParentBlockHash(),
              bid.getParentBlockRoot()));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, bid.getParentBlockRoot(), bid.getSlot())
        .thenApply(
            maybeState -> {
              /*
               * [IGNORE] The bid's parent block has been imported
               * (MAY be queued until parent is imported)
               */
              if (maybeState.isEmpty()) {
                return saveBidForFuture(
                    bid,
                    "state for parent block root %s at slot %s is unavailable; saving for future processing",
                    bid.getParentBlockRoot(),
                    parentBlockSlot);
              }
              final BeaconState state = maybeState.get();

              /*
               * Check again because the head may have changed while retrieving the state
               *
               * [IGNORE] The bid is compatible with the current head branch
               */
              if (!gossipValidationHelper.isBidCompatibleWithHead(bid)) {
                return ignoreBid(
                    bid,
                    "is not compatible with the current head branch (parent block hash %s, parent block root %s)",
                    bid.getParentBlockHash(),
                    bid.getParentBlockRoot());
              }

              /*
               * [REJECT] The bid's previous randao is correct
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

              final SszList<Builder> builders = BeaconStateGloas.required(state).getBuilders();

              /*
               * [REJECT] The builder index is valid
               */
              if (bid.getBuilderIndex().isGreaterThanOrEqualTo(builders.size())) {
                return rejectBid(
                    bid,
                    "builder index %s is out of range for the %s builders in the state",
                    bid.getBuilderIndex(),
                    builders.size());
              }

              /*
               * [REJECT] The builder is active
               */

              if (!gossipValidationHelper.isActiveBuilder(
                  bid.getBuilderIndex(), state, bid.getSlot())) {
                return rejectBid(
                    bid,
                    "builder index %s is not valid, active, and non-slashed",
                    bid.getBuilderIndex());
              }

              /*
               * [REJECT] The builder is a payload builder
               */
              final Builder builder = builders.get(bid.getBuilderIndex().intValue());
              final int builderVersion = builder.getVersion();
              if (builderVersion != PAYLOAD_BUILDER_VERSION) {
                return rejectBid(
                    bid,
                    "builder index %s has version %s but only payload builder version %s may bid",
                    bid.getBuilderIndex(),
                    builderVersion,
                    PAYLOAD_BUILDER_VERSION);
              }

              /*
               * [IGNORE] The builder can cover the bid
               */
              if (!gossipValidationHelper.builderHasEnoughBalanceForBid(
                  bid.getValue(), bid.getBuilderIndex(), state, bid.getSlot())) {
                return ignoreBid(bid, "value exceeds the builder's excess balance");
              }

              /*
               * [IGNORE] The parent's payload does not try to exit the builder
               */
              if (bid.getParentBlockHash()
                  .equals(
                      BeaconStateGloas.required(state)
                          .getLatestExecutionPayloadBid()
                          .getBlockHash())) {
                if (spec.isExecutionPayloadEnvelopeAvailableAtSlot(parentBlockSlot)) {
                  final Optional<SignedExecutionPayloadEnvelope> maybeParentPayload =
                      gossipValidationHelper.getRecentlyImportedExecutionPayload(
                          bid.getParentBlockRoot());
                  if (maybeParentPayload.isEmpty()) {
                    return saveBidForFuture(
                        bid,
                        "parent execution payload is unavailable. Saving for future processing");
                  }
                  final boolean parentPayloadMayExitBuilder =
                      ExecutionRequestsGloas.required(
                              maybeParentPayload.get().getMessage().getExecutionRequests())
                          .getBuilderExits()
                          .stream()
                          .anyMatch(
                              request ->
                                  request.getPubkey().equals(builder.getPublicKey())
                                      && request
                                          .getSourceAddress()
                                          .getWrappedBytes()
                                          .equals(builder.getExecutionAddress().getWrappedBytes()));
                  if (parentPayloadMayExitBuilder) {
                    return ignoreBid(
                        bid, "parent payload may exit builder %s", bid.getBuilderIndex());
                  }
                }
              }

              /*
               * [REJECT] The bid signature is valid
               */
              if (!isSignatureValid(signedExecutionPayloadBid, state)) {
                return rejectBid(bid, "invalid execution payload bid signature");
              }

              final UInt64 bidValue = bid.getValue();
              final AtomicReference<InternalValidationResult> ignoreResult =
                  new AtomicReference<>();
              highestBids.compute(
                  bidValueKey,
                  (key, existingValue) -> {
                    if (seenExecutionPayloadBids
                        .getOrDefault(bid.getSlot(), Set.of())
                        .contains(builderAndParent)) {
                      ignoreResult.set(
                          ignoreBid(
                              bid,
                              "another bid for parent block hash %s and parent block root %s was processed concurrently",
                              bid.getParentBlockHash(),
                              bid.getParentBlockRoot()));
                      return existingValue;
                    }

                    if (existingValue != null) {
                      final UInt64 minimum = calculateMinimumRequiredBid(existingValue);
                      if (bidValue.isLessThan(minimum)) {
                        ignoreResult.set(
                            ignoreBid(
                                bid,
                                "does not meet minimum increment threshold (%s%%) after a concurrent update; current highest is %s ETH and minimum required is %s ETH",
                                minBidIncrementPercentage,
                                gweiToEth(existingValue),
                                gweiToEth(minimum)));
                        return existingValue;
                      }
                    }

                    seenExecutionPayloadBids
                        .computeIfAbsent(bid.getSlot(), __ -> ConcurrentHashMap.newKeySet())
                        .add(builderAndParent);
                    return bidValue;
                  });

              return Optional.ofNullable(ignoreResult.get()).orElseGet(() -> acceptBid(bid));
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

  public static boolean isGasLimitTargetCompatible(
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
