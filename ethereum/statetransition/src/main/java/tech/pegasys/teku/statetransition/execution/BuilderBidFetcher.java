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

import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.StakedBuilderClientProvider;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;

/**
 * Pulls bids over the Builder API, by sending an HTTP request to each builder configured in the
 * {@link BuilderConfig}.
 *
 * <p>This is one of the two sources of remote bids. The other one is the ePBS gossip topic, served
 * by {@link DefaultExecutionPayloadBidManager}; those bids are validated on the gossip path
 * instead. Bids from both sources compete in {@link ExecutionPayloadBidSelector}, where a bid
 * fetched here is the only kind carrying a {@link BuilderEntry}.
 */
public class BuilderBidFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final StakedBuilderClientProvider stakedBuilderClientProvider;
  private final BuilderBidValidator bidValidator;

  public BuilderBidFetcher(
      final Spec spec,
      final StakedBuilderClientProvider stakedBuilderClientProvider,
      final BuilderBidValidator bidValidator) {
    this.spec = spec;
    this.stakedBuilderClientProvider = stakedBuilderClientProvider;
    this.bidValidator = bidValidator;
  }

  /**
   * Requests a bid from every configured builder in parallel and returns the ones that both
   * answered and passed validation, so a failing or misbehaving builder cannot hold up the others.
   *
   * @return the valid bids, which may be empty, never a failed future
   */
  public SafeFuture<List<RemoteBid>> getBuilderBids(
      final BeaconState state,
      final UInt64 slot,
      final BuilderConfig builderConfig,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BlockProductionPerformance blockProductionPerformance) {
    final SszList<BuilderEntry> configuredBuilders = builderConfig.getBuilders();
    if (configuredBuilders.isEmpty()) {
      return SafeFuture.completedFuture(Collections.emptyList());
    }
    final int proposerIndex =
        spec.atSlot(slot).beaconStateAccessors().getBeaconProposerIndex(state, slot);
    final BLSPublicKey proposerPubkey =
        spec.getValidatorPubKey(state, UInt64.valueOf(proposerIndex)).orElseThrow();
    // Tracks how many builder responses (successful or not) are still outstanding, so
    // blockProductionPerformance.builderGetHeader() can fire as soon as the last one lands, before
    // validation runs.
    final AtomicInteger pendingResponses = new AtomicInteger(configuredBuilders.size());
    final Stream<SafeFuture<Optional<RemoteBid>>> builderBids =
        configuredBuilders.stream()
            .map(
                builderEntry ->
                    getExecutionPayloadBid(
                            slot, parentHash, parentRoot, proposerPubkey, builderEntry)
                        .alwaysRun(
                            () -> {
                              if (pendingResponses.decrementAndGet() == 0) {
                                blockProductionPerformance.builderGetHeader();
                              }
                            })
                        .thenApply(
                            maybeBid ->
                                maybeBid
                                    .filter(
                                        bid ->
                                            validateBid(
                                                bid, state, parentHash, parentRoot, builderEntry))
                                    .map(bid -> createRemoteBid(bid, builderEntry)))
                        .whenComplete(
                            (maybeBid, exception) -> {
                              if (exception != null) {
                                LOG.error(
                                    "Error while retrieving an execution payload bid from {}",
                                    builderEntry.getUrl(),
                                    exception);
                                return;
                              }
                              maybeBid.ifPresentOrElse(
                                  bid ->
                                      LOG.info(
                                          "Retrieved bid from {} (builder index: {}, value: {} ETH) for block at slot {}",
                                          builderEntry.getUrl(),
                                          bid.bid().getMessage().getBuilderIndex(),
                                          gweiToEth(bid.valueInGwei()),
                                          slot),
                                  () ->
                                      LOG.info(
                                          "No bid available from {} for block at slot {}",
                                          builderEntry.getUrl(),
                                          slot));
                            }));
    return SafeFuture.collectAllSuccessful(builderBids)
        .thenApply(
            bids -> {
              final List<RemoteBid> validatedBids =
                  bids.stream().flatMap(Optional::stream).toList();
              blockProductionPerformance.builderBidValidated();
              return validatedBids;
            });
  }

  private SafeFuture<Optional<SignedExecutionPayloadBid>> getExecutionPayloadBid(
      final UInt64 slot,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BLSPublicKey proposerPubkey,
      final BuilderEntry builderEntry) {
    return SafeFuture.of(
        () ->
            stakedBuilderClientProvider
                .getClient(builderEntry.getUrl())
                .getExecutionPayloadBid(
                    slot, parentHash, parentRoot, proposerPubkey, builderEntry.getAuth()));
  }

  private boolean validateBid(
      final SignedExecutionPayloadBid bid,
      final BeaconState state,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BuilderEntry builderEntry) {
    try {
      return bidValidator.validateBid(bid, state, parentHash, parentRoot, builderEntry);
    } catch (final Exception ex) {
      LOG.warn(
          "Exception occurred while validating a bid from {} (builder {})",
          builderEntry.getUrl(),
          bid.getMessage().getBuilderIndex(),
          ex);
      return false;
    }
  }

  private RemoteBid createRemoteBid(
      final SignedExecutionPayloadBid bid, final BuilderEntry builderEntry) {
    final UInt64 valueInGwei =
        getBidValueInGwei(bid, builderEntry.getMaxExecutionPayment(), builderEntry.getUrl());
    return new RemoteBid(bid, valueInGwei, Optional.of(builderEntry));
  }

  // For bids received via the builder API, the total bid
  // score accounts for both the on-chain collateral commitment
  // and the trusted execution layer payment, capped at the
  // `max_execution_payment` the validator advertised
  private UInt64 getBidValueInGwei(
      final SignedExecutionPayloadBid bid, final UInt64 maxExecutionPayment, final String url) {
    try {
      final UInt64 trustedExecutionPayment =
          bid.getMessage().getExecutionPayment().min(maxExecutionPayment);
      return bid.getMessage().getValue().plus(trustedExecutionPayment);
    } catch (final ArithmeticException ex) {
      LOG.warn(
          "Failed to compute bid value for a bid coming from {} (builder {})",
          url,
          bid.getMessage().getBuilderIndex());
      return UInt64.ZERO;
    }
  }
}
