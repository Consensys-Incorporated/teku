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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManager
    implements ExecutionPayloadBidManager,
        SlotEventsChannel,
        ReceivedBlockEventsChannel,
        OperationAddedSubscriber<SignedProposerPreferences>,
        ReceivedExecutionPayloadEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator;
  private final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker;
  private final ReceivedExecutionPayloadBidEventsChannel
      receivedExecutionPayloadBidEventsChannelPublisher;
  private final PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids;
  private final Subscribers<OperationAddedSubscriber<SignedExecutionPayloadBid>> subscribers =
      Subscribers.create(true);
  private final BuilderBidFetcher builderBidFetcher;
  private final ExecutionPayloadBidSelector bidSelector;

  // bids are valid for the current and next slot, so they're indexed by bid.slot for pruning;
  // Sorting bids is only needed during block production, which occurs infrequently. To prevent
  // unnecessary insertion overhead the rest of the time, we keep them in a standard set.
  private final ConcurrentNavigableMap<UInt64, Set<SignedExecutionPayloadBid>> bidsBySlot =
      new ConcurrentSkipListMap<>();

  public DefaultExecutionPayloadBidManager(
      final Spec spec,
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator,
      final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker,
      final ReceivedExecutionPayloadBidEventsChannel
          receivedExecutionPayloadBidEventsChannelPublisher,
      final PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids,
      final BuilderBidFetcher builderBidFetcher,
      final ExecutionPayloadBidSelector bidSelector) {
    this.spec = spec;
    this.executionPayloadBidGossipValidator = executionPayloadBidGossipValidator;
    this.executionPayloadBidCircuitBreaker = executionPayloadBidCircuitBreaker;
    this.receivedExecutionPayloadBidEventsChannelPublisher =
        receivedExecutionPayloadBidEventsChannelPublisher;
    this.pendingExecutionPayloadBids = pendingExecutionPayloadBids;
    this.builderBidFetcher = builderBidFetcher;
    this.bidSelector = bidSelector;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
    return validateAndAddBid(signedBid, remoteBidOrigin == RemoteBidOrigin.P2P);
  }

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedExecutionPayloadBid> subscriber) {
    subscribers.subscribe(subscriber);
  }

  private SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final boolean fromNetwork) {
    return executionPayloadBidGossipValidator
        .validate(signedBid)
        .thenApply(
            result -> {
              processValidationResult(signedBid, fromNetwork, result);
              return result;
            });
  }

  private void processValidationResult(
      final SignedExecutionPayloadBid signedBid,
      final boolean fromNetwork,
      final InternalValidationResult result) {
    switch (result.code()) {
      case ACCEPT -> {
        addBid(signedBid);
        receivedExecutionPayloadBidEventsChannelPublisher.onExecutionPayloadBidValidated(signedBid);
        subscribers.forEach(
            subscriber -> subscriber.onOperationAdded(signedBid, result, fromNetwork));
      }
      case SAVE_FOR_FUTURE -> pendingExecutionPayloadBids.add(signedBid);
      case REJECT, IGNORE ->
          LOG.debug(
              "Wouldn't consider bid for slot {} from builder {} because it didn't pass gossip validation: {}",
              signedBid.getMessage().getSlot(),
              signedBid.getMessage().getBuilderIndex(),
              result);
    }
  }

  private void addBid(final SignedExecutionPayloadBid signedBid) {
    bidsBySlot
        .computeIfAbsent(signedBid.getMessage().getSlot(), __ -> ConcurrentHashMap.newKeySet())
        .add(signedBid);
  }

  private void retryPendingBids(final Collection<SignedExecutionPayloadBid> pendingBids) {
    // As with non-deferred bids, gossip validation accepts the first valid bid for each
    // (slot, builder index). Reconsider ordering if the spec allows multiple bids per tuple.
    // Deferred gossip was ignored by gossipsub, so accepted retries must be explicitly published.
    pendingBids.forEach(pendingBid -> validateAndAddBid(pendingBid, false).finishError(LOG));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // bids are valid for the current and next slot, so anything below the current slot is stale
    bidsBySlot.headMap(slot, false).clear();
    pendingExecutionPayloadBids.onSlot(slot);
    // PendingPool prunes historical items only once per epoch, so remove stale bids before retrying
    pendingExecutionPayloadBids.removeItemsMatching(
        pendingBid -> pendingBid.getMessage().getSlot().isLessThan(slot));
    retryPendingBids(pendingExecutionPayloadBids.removeItemsMatching(__ -> true));
  }

  @Override
  public void onOperationAdded(
      final SignedProposerPreferences proposerPreferences,
      final InternalValidationResult validationStatus,
      final boolean fromNetwork) {
    final UInt64 proposalSlot = proposerPreferences.getMessage().getProposalSlot();
    retryPendingBids(
        pendingExecutionPayloadBids.removeItemsMatching(
            pendingBid -> pendingBid.getMessage().getSlot().equals(proposalSlot)));
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    executionPayloadBidCircuitBreaker.observeImportedBlock(block);
    retryPendingBids(pendingExecutionPayloadBids.removeItemsDependingOn(block.getRoot()));
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    retryPendingBids(
        pendingExecutionPayloadBids.removeItemsDependingOn(executionPayload.getBeaconBlockRoot()));
  }

  @Override
  public SafeFuture<SignedExecutionPayloadBid> getBidForBlock(
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final BuilderConfig builderConfig,
      final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slot = state.getSlot();
    final SafeFuture<Optional<SignedExecutionPayloadBid>> remoteBidFuture;
    if (executionPayloadBidCircuitBreaker.isEngaged(parentRoot, state)) {
      LOG.info("Builder circuit breaker engaged for block at slot {}; self-building", slot);
      remoteBidFuture = SafeFuture.completedFuture(Optional.empty());
    } else {
      // Remote bids include the bids retrieved from configured builders plus any valid p2p bids
      // received by block proposal time
      remoteBidFuture =
          builderBidFetcher
              .getBuilderBids(state, slot, builderConfig, parentBlockHash, parentRoot)
              .thenApply(
                  builderBids -> {
                    final Set<SignedExecutionPayloadBid> p2pBids = getP2PBidsForSlot(slot);
                    return bidSelector.selectBestRemoteBid(
                        p2pBids, builderBids, parentRoot, parentBlockHash, state);
                  });
    }

    final SafeFuture<Optional<LocalBid>> localBidFuture =
        getPayloadResponseFuture
            .thenApply(
                getPayloadResponse -> {
                  final Bytes32 localParentBlockHash =
                      getPayloadResponse.getExecutionPayload().getParentHash();
                  Preconditions.checkState(
                      localParentBlockHash.equals(parentBlockHash),
                      "Local execution payload parent hash %s does not match selected production parent execution hash %s for block at slot %s",
                      localParentBlockHash,
                      parentBlockHash,
                      slot);
                  return new LocalBid(
                      createLocalSelfBuiltSignedBid(getPayloadResponse, slot, parentRoot),
                      getPayloadResponse.getExecutionPayloadValue(),
                      getPayloadResponse.getShouldOverrideBuilder());
                })
            .thenApply(Optional::of)
            .exceptionally(
                error -> {
                  LOG.warn(
                      "Local execution payload is unavailable for block at slot {}. Will attempt to select a remote bid instead.",
                      slot,
                      error);
                  return Optional.empty();
                });

    return localBidFuture.thenCombine(
        remoteBidFuture,
        (maybeLocalBid, maybeRemoteBid) ->
            bidSelector.selectBestBid(maybeLocalBid, maybeRemoteBid, builderConfig, slot));
  }

  @VisibleForTesting
  Set<SignedExecutionPayloadBid> getP2PBidsForSlot(final UInt64 slot) {
    return bidsBySlot.getOrDefault(slot, Collections.emptySet());
  }

  private SignedExecutionPayloadBid createLocalSelfBuiltSignedBid(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    final SpecVersion specVersion = spec.atSlot(slot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(specVersion.getSchemaDefinitions());
    final ExecutionPayload executionPayload = getPayloadResponse.getExecutionPayload();
    final SszList<SszKZGCommitment> blobKzgCommitments =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(getPayloadResponse.getBlobsBundle().orElseThrow());
    final Bytes32 executionRequestsRoot =
        getPayloadResponse.getExecutionRequests().orElseThrow().hashTreeRoot();

    final ExecutionPayloadBid bid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                parentRoot, slot, executionPayload, blobKzgCommitments, executionRequestsRoot);
    // Using G2_POINT_AT_INFINITY as signature for self-builds
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, BLSSignature.infinity());
  }

  public record LocalBid(
      SignedExecutionPayloadBid bid, UInt256 valueInWei, boolean shouldOverrideBuilder) {}
}
