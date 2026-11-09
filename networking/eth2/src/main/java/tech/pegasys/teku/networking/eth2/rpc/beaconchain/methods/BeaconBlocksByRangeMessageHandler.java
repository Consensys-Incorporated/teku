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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.InvalidRpcMethodVersion;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BeaconBlocksByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlocksRequestedCounter;

  public BeaconBlocksByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_range_requests_total",
            "Total number of blocks by range requests received",
            "status");
    totalBlocksRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_range_requested_blocks_total",
            "Total number of blocks requested in accepted blocks by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BeaconBlocksByRangeRequestMessage request) {
    final int version = BeaconChainMethodIds.extractBeaconBlocksByRangeVersion(protocolId);
    final SpecMilestone latestMilestoneRequested;
    try {
      latestMilestoneRequested =
          spec.getForkSchedule().getSpecMilestoneAtSlot(request.getMaxSlot());
    } catch (final ArithmeticException __) {
      return Optional.of(
          new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    }

    final boolean isAltairActive =
        latestMilestoneRequested.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR);

    if (version == 1 && isAltairActive) {
      return Optional.of(
          new InvalidRpcMethodVersion("Must request altair blocks using v2 protocol"));
    }

    if (request.getStep().isLessThan(ONE)) {
      requestCounter.labels("invalid_step").inc();
      return Optional.of(new RpcException(INVALID_REQUEST_CODE, "Step must be greater than zero"));
    }

    final int maxRequestBlocks =
        spec.forMilestone(latestMilestoneRequested).miscHelpers().getMaxRequestBlocks().intValue();

    int requestedCount;
    try {
      requestedCount = request.getCount().intValue();
    } catch (final ArithmeticException __) {
      // handle overflows
      requestedCount = -1;
    }

    if (requestedCount == -1 || requestedCount > maxRequestBlocks) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of " + maxRequestBlocks + " blocks can be requested per request"));
    }

    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks starting at slot {} with step {}",
        peer.getId(),
        message.getCount(),
        message.getStartSlot(),
        message.getStep());

    final Optional<RequestKey> maybeRequestKey =
        peer.approveBlocksRequest(callback, message.getCount().longValue());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlocksRequestedCounter.inc(message.getCount().longValue());
    sendMatchingBlocks(message, callback)
        .finish(
            requestState -> {
              if (requestState.sentBlocks.get() != message.getCount().longValue()) {
                peer.adjustBlocksRequest(maybeRequestKey.get(), requestState.sentBlocks.get());
              }
              callback.completeSuccessfully();
            },
            error -> handleError(error, callback, "blocks by range"));
  }

  private SafeFuture<RequestState> sendMatchingBlocks(
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 count = message.getCount();
    final UInt64 step = message.getStep();

    return combinedChainDataClient
        .getEarliestAvailableBlockSlot()
        .thenCompose(
            earliestSlot -> {
              if (earliestSlot.map(s -> s.isGreaterThan(startSlot)).orElse(true)) {
                // We're missing the first block so return an error
                return SafeFuture.failedFuture(
                    new RpcException.ResourceUnavailableException(
                        "Requested historical blocks are currently unavailable"));
              }
              final UInt64 headBlockSlot =
                  combinedChainDataClient
                      .getChainHead()
                      .map(MinimalBeaconBlockSummary::getSlot)
                      .orElse(ZERO);
              final NavigableMap<UInt64, Bytes32> hotRoots;
              if (combinedChainDataClient.isFinalized(message.getMaxSlot())) {
                // All blocks are finalized so skip scanning the protoarray
                hotRoots = new TreeMap<>();
              } else {
                hotRoots = combinedChainDataClient.getAncestorRoots(startSlot, step, count);
              }
              // Don't send anything past the last slot found in protoarray to ensure blocks are
              // consistent
              // If we didn't find any blocks in protoarray, every block in the range must be
              // finalized
              // so we don't need to worry about inconsistent blocks
              final UInt64 headSlot = hotRoots.isEmpty() ? headBlockSlot : hotRoots.lastKey();
              final RequestState initialState =
                  new RequestState(startSlot, step, count, headSlot, hotRoots, callback);
              // Only short-circuit when there is genuinely nothing to send: a zero-length request
              // or a start slot strictly beyond our head. When startSlot == headSlot we must still
              // return the block at that slot, since the spec requires returning at least the first
              // block in the requested range if we have it.
              if (count.isZero() || startSlot.isGreaterThan(headSlot)) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendNextBlock(initialState);
            });
  }

  private SafeFuture<RequestState> sendNextBlock(final RequestState requestState) {
    SafeFuture<Boolean> blockFuture = processNextBlock(requestState);
    // Avoid risk of StackOverflowException by iterating when the block future is already complete
    // Using thenCompose on the completed future would execute immediately and recurse back into
    // this method to send the next block.  When not already complete, thenCompose is executed
    // on a separate thread so doesn't recurse on the same stack.
    while (blockFuture.isDone() && !blockFuture.isCompletedExceptionally()) {
      if (blockFuture.join()) {
        return completedFuture(requestState);
      }
      blockFuture = processNextBlock(requestState);
    }
    return blockFuture.thenCompose(
        complete -> complete ? completedFuture(requestState) : sendNextBlock(requestState));
  }

  private SafeFuture<Boolean> processNextBlock(final RequestState requestState) {
    // Ensure blocks are loaded off of the event thread
    return requestState
        .loadNextBlock()
        .thenCompose(
            block -> {
              requestState.decrementRemainingBlocks();
              return handleLoadedBlock(requestState, block);
            });
  }

  /** Sends the block and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedBlock(
      final RequestState requestState, final Optional<SignedBeaconBlock> block) {
    return block
        .map(requestState::sendBlock)
        .orElse(SafeFuture.COMPLETE)
        .thenApply(
            __ -> {
              if (requestState.isComplete()) {
                return true;
              } else {
                requestState.incrementCurrentSlot();
                return false;
              }
            });
  }

  private class RequestState {

    private final UInt64 headSlot;
    private final ResponseCallback<SignedBeaconBlock> callback;
    private final UInt64 step;
    private final NavigableMap<UInt64, Bytes32> knownBlockRoots;
    private UInt64 currentSlot;
    private UInt64 remainingBlocks;

    private final AtomicInteger sentBlocks = new AtomicInteger(0);

    RequestState(
        final UInt64 startSlot,
        final UInt64 step,
        final UInt64 count,
        final UInt64 headSlot,
        final NavigableMap<UInt64, Bytes32> knownBlockRoots,
        final ResponseCallback<SignedBeaconBlock> callback) {
      this.currentSlot = startSlot;
      this.knownBlockRoots = knownBlockRoots;
      this.remainingBlocks = count;
      this.step = step;
      this.headSlot = headSlot;
      this.callback = callback;
    }

    private boolean needsMoreBlocks() {
      return !remainingBlocks.equals(ZERO);
    }

    private boolean hasReachedHeadSlot() {
      return currentSlot.compareTo(headSlot) >= 0;
    }

    boolean isComplete() {
      return !needsMoreBlocks() || hasReachedHeadSlot();
    }

    SafeFuture<Void> sendBlock(final SignedBeaconBlock block) {
      // request step is deprecated, if a step greater than 1 is requested, only return the first
      // block
      if (step.isGreaterThan(1L)) {
        remainingBlocks = ZERO;
      }
      return callback.respond(block).thenRun(sentBlocks::incrementAndGet);
    }

    void decrementRemainingBlocks() {
      remainingBlocks = remainingBlocks.minusMinZero(1);
    }

    void incrementCurrentSlot() {
      currentSlot = currentSlot.plus(step);
    }

    SafeFuture<Optional<SignedBeaconBlock>> loadNextBlock() {
      final UInt64 slot = this.currentSlot;
      final Bytes32 knownBlockRoot = knownBlockRoots.get(slot);
      if (knownBlockRoot != null) {
        // Known root so lookup by root
        return combinedChainDataClient
            .getBlockByBlockRoot(knownBlockRoot)
            .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slot)));
      } else if ((!knownBlockRoots.isEmpty() && slot.compareTo(knownBlockRoots.firstKey()) >= 0)
          || slot.compareTo(headSlot) > 0) {
        // Unknown root but not finalized means this is an empty slot
        // Could also be because the first block requested is above our head slot
        return SafeFuture.completedFuture(Optional.empty());
      } else {
        // Must be a finalized block so lookup by slot
        return combinedChainDataClient.getBlockAtSlotExact(slot);
      }
    }
  }
}
