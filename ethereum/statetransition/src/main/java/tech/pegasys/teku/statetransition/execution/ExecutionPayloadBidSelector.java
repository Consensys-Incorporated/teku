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

import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatAbbreviatedHashRoot;
import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorFormatter;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.BidForBlock;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.LocalBid;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;

public class ExecutionPayloadBidSelector {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean useShouldOverrideBuilderFlag;
  private final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker;

  private static final Comparator<RemoteBid> REMOTE_BID_BY_VALUE_ASCENDING =
      Comparator.comparing(RemoteBid::valueInGwei);

  public ExecutionPayloadBidSelector(
      final boolean useShouldOverrideBuilderFlag,
      final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker) {
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
    this.executionPayloadBidCircuitBreaker = executionPayloadBidCircuitBreaker;
  }

  /**
   * Selects the highest-value bid from p2p and builder bids. P2P bids are filtered by parent root,
   * parent block hash, min bid, and {@code isBuilderAllowed}; builder bids are filtered by {@code
   * isBuilderAllowed} only because all validation is done during fetching the bids. On equal value,
   * the builder bid is preferred.
   */
  public Optional<RemoteBid> selectBestRemoteBid(
      final Set<RemoteBid> p2pBids,
      final List<RemoteBid> builderBids,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state,
      final BuilderConfig builderConfig) {
    final Optional<RemoteBid> bestP2PBid =
        p2pBids.stream()
            .filter(bid -> bid.bid().getMessage().getParentBlockRoot().equals(parentRoot))
            .filter(bid -> bid.bid().getMessage().getParentBlockHash().equals(parentBlockHash))
            .filter(
                bid ->
                    executionPayloadBidCircuitBreaker.isBuilderAllowed(bid.builderIndex(), state))
            // A bid is eligible only if `bid_score >= min_bid
            .filter(bid -> bid.valueInGwei().isGreaterThanOrEqualTo(builderConfig.getMinBid()))
            .max(REMOTE_BID_BY_VALUE_ASCENDING);

    final Optional<RemoteBid> bestBuilderBid =
        builderBids.stream()
            .filter(
                bid ->
                    executionPayloadBidCircuitBreaker.isBuilderAllowed(bid.builderIndex(), state))
            .max(REMOTE_BID_BY_VALUE_ASCENDING);

    if (bestBuilderBid.isEmpty()) {
      return bestP2PBid;
    }
    if (bestP2PBid.isEmpty()) {
      return bestBuilderBid;
    }
    // on equal value, prefer the builder bid
    return bestBuilderBid.get().valueInGwei().isGreaterThanOrEqualTo(bestP2PBid.get().valueInGwei())
        ? bestBuilderBid
        : bestP2PBid;
  }

  /**
   * Selects between a remote bid and the locally built execution payload.
   *
   * <p>If {@code maybeRemoteBid} is empty, the local bid is returned unconditionally (the caller
   * already decided to self-build). If {@code maybeLocalBid} is empty (local payload unavailable),
   * the remote bid is returned unconditionally. When both are present, selection follows this
   * precedence:
   *
   * <ol>
   *   <li>If {@code useShouldOverrideBuilderFlag} is enabled and the EL returns {@code
   *       should_override_builder}, the local payload wins.
   *   <li>Otherwise, {@code builderConfig.getBuilderBoostFactor()} is used to scale the remote
   *       value. Equal adjusted values select the local payload.
   * </ol>
   */
  public BidForBlock selectBestBidForBlock(
      final Optional<LocalBid> maybeLocalBid,
      final Optional<RemoteBid> maybeRemoteBid,
      final BuilderConfig builderConfig,
      final UInt64 slot) {

    if (maybeRemoteBid.isEmpty()) {
      return selectLocalBid(
          maybeLocalBid.orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format(
                          "No remote or self-built bid is available for block at slot %s.", slot))),
          slot);
    }

    final RemoteBid remoteBid = maybeRemoteBid.get();
    final UInt256 remoteValueInWei =
        UInt256.valueOf(remoteBid.valueInGwei().bigIntegerValue()).multiply(GWEI_TO_WEI);

    if (maybeLocalBid.isEmpty()) {
      return selectRemoteBid(remoteBid, slot, remoteValueInWei);
    }

    final LocalBid localBid = maybeLocalBid.get();
    if (useShouldOverrideBuilderFlag && localBid.shouldOverrideBuilder()) {
      LOG.info(
          "Selected self-built bid for block at slot {} because should_override_builder is true",
          slot);
      return selectLocalBid(localBid, slot);
    }

    final UInt64 builderBoostFactor = builderConfig.getBuilderBoostFactor();
    final boolean localValueWins =
        BuilderBoostFactorEvaluator.isLocalValueWinning(
            localBid.valueInWei(), remoteValueInWei, builderBoostFactor);
    logValueComparison(
        localValueWins, builderBoostFactor, localBid.valueInWei(), remoteValueInWei, slot);
    return localValueWins
        ? selectLocalBid(localBid, slot)
        : selectRemoteBid(remoteBid, slot, remoteValueInWei);
  }

  private BidForBlock selectLocalBid(final LocalBid localBid, final UInt64 slot) {
    LOG.info(
        "Using self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
        weiToEth(localBid.valueInWei()),
        formatAbbreviatedHashRoot(localBid.bid().getMessage().getBlockHash()),
        slot);
    return new BidForBlock(localBid.bid(), localBid.valueInWei(), Optional.empty());
  }

  private BidForBlock selectRemoteBid(
      final RemoteBid remoteBid, final UInt64 slot, final UInt256 remoteValueInWei) {
    LOG.info(
        "Using remote bid (value: {} ETH, builder index: {}, EL block: {}) for block at slot {}",
        weiToEth(remoteValueInWei),
        remoteBid.builderIndex(),
        formatAbbreviatedHashRoot(remoteBid.bid().getMessage().getBlockHash()),
        slot);
    return new BidForBlock(remoteBid.bid(), remoteValueInWei, remoteBid.builderUrl());
  }

  private void logValueComparison(
      final boolean localValueWins,
      final UInt64 builderBoostFactor,
      final UInt256 localValue,
      final UInt256 remoteValue,
      final UInt64 slot) {
    LOG.info(
        "{} - builder boost factor: {}, source: VC.",
        localValueWins
            ? String.format(
                "Local execution payload (%s ETH) is chosen over remote bid (%s ETH) for block at slot %s",
                weiToEth(localValue), weiToEth(remoteValue), slot)
            : String.format(
                "Remote bid (%s ETH) is chosen over local execution payload (%s ETH) for block at slot %s",
                weiToEth(remoteValue), weiToEth(localValue), slot),
        BuilderBoostFactorFormatter.formatBuilderBoostFactor(builderBoostFactor));
  }
}
