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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import com.google.common.annotations.VisibleForTesting;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyrange-v1">ExecutionPayloadEnvelopesByRange</a>
 */
public class ExecutionPayloadEnvelopesByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SpecConfigGloas config;
  private final LabelledMetric<Counter> requestCounter;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;

  public ExecutionPayloadEnvelopesByRangeMessageHandler(
      final Spec spec,
      final SpecConfigGloas config,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.config = config;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_range_requests_total",
            "Total number of execution payload envelopes by range requests received",
            "status");
    totalExecutionPayloadEnvelopesRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_by_range_requested_envelopes_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final ExecutionPayloadEnvelopesByRangeRequestMessage request) {
    try {
      request.getMaxSlot();
    } catch (final ArithmeticException __) {
      return Optional.of(
          new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    }

    if (request.getCount().isGreaterThan(config.getMaxRequestPayloads())) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s execution payload envelopes can be requested per request",
                  config.getMaxRequestPayloads())));
    }
    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRangeRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 count = message.getCount();
    LOG.trace(
        "Peer {} requested {} execution payload envelopes starting at slot {}",
        peer.getId(),
        count,
        startSlot);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveExecutionPayloadEnvelopesRequest(callback, count.longValue());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }
    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(count.longValue());

    // Trim the requested range down to the slots we are required to serve. Retrieving an envelope
    // for a finalized block requires reading the blinded envelope from the database and unblinding
    // it with an execution layer round trip, so slots outside the servable range are dropped before
    // any of that work is done. The count is reduced along with the start slot so that we never
    // respond with envelopes from outside the range the peer asked for.
    final UInt64 minServableSlot = getMinServableSlot();
    final UInt64 adjustedStartSlot = startSlot.max(minServableSlot);
    final UInt64 adjustedCount = count.minusMinZero(adjustedStartSlot.minusMinZero(startSlot));

    // Envelopes are only served for payloads that are part of the chain defined by the current
    // head: the fork choice node of the block on the head chain must be FULL. Slots from the
    // finalized block onwards are resolved on the head chain, which decides the payload status of
    // the finalized block itself. Slots before the finalized block are served from the finalized
    // database, which only retains envelopes of payloads the finalized chain built on.
    final NavigableMap<UInt64, ForkChoiceNode> hotNodes;

    final UInt64 finalizedBlockSlot = combinedChainDataClient.getFinalizedBlockSlot().orElse(ZERO);
    if (message.getMaxSlot().isLessThan(finalizedBlockSlot)) {
      // All requested slots are before the finalized block so skip scanning the protoarray
      hotNodes = new TreeMap<>();
    } else {
      hotNodes = combinedChainDataClient.getAncestorNodes(adjustedStartSlot, adjustedCount);
    }

    final UInt64 headSlot =
        hotNodes.isEmpty()
            ? combinedChainDataClient
                .getChainHead()
                .map(MinimalBeaconBlockSummary::getSlot)
                .orElse(ZERO)
            : hotNodes.lastKey();

    final RequestState initialState =
        new RequestState(callback, adjustedStartSlot, adjustedCount, headSlot, hotNodes);

    final SafeFuture<RequestState> response;

    // Only short-circuit when there is genuinely nothing to send: an empty range or a start slot
    // strictly beyond our head. When the start slot equals the head slot we must still return the
    // envelope at that slot, which is also the range a request trimmed down to its last servable
    // slot ends up with.
    if (adjustedCount.isZero() || adjustedStartSlot.isGreaterThan(headSlot)) {
      response = SafeFuture.completedFuture(initialState);
    } else {
      response = sendNextExecutionPayloadEnvelope(initialState);
    }

    response.finish(
        requestState -> {
          if (requestState.sentExecutionPayloadEnvelopes.get() != count.longValue()) {
            peer.adjustExecutionPayloadEnvelopesRequest(
                maybeRequestKey.get(), requestState.sentExecutionPayloadEnvelopes.get());
          }
          callback.completeSuccessfully();
        },
        err -> handleError(err, callback, "execution payload envelopes by range"));
  }

  /**
   * The first slot we are required to serve envelopes for: the start of the earliest epoch within
   * {@code MIN_EPOCHS_FOR_BLOCK_REQUESTS} of the current epoch, never earlier than the Gloas fork.
   */
  private UInt64 getMinServableSlot() {
    final UInt64 currentEpoch =
        combinedChainDataClient.getRecentChainData().getCurrentEpoch().orElse(ZERO);
    final UInt64 minServableEpoch =
        currentEpoch
            .minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests())
            .max(spec.getSpecConfig(currentEpoch).getGloasForkEpoch());
    return spec.computeStartSlotAtEpoch(minServableEpoch);
  }

  private SafeFuture<RequestState> sendNextExecutionPayloadEnvelope(
      final RequestState requestState) {
    SafeFuture<Boolean> executionPayloadFuture = processNextExecutionPayloadEnvelope(requestState);
    // similar logic to BeaconBlocksByRange
    while (executionPayloadFuture.isDone() && !executionPayloadFuture.isCompletedExceptionally()) {
      if (executionPayloadFuture.join()) {
        return completedFuture(requestState);
      }
      executionPayloadFuture = processNextExecutionPayloadEnvelope(requestState);
    }
    return executionPayloadFuture.thenCompose(
        complete ->
            complete
                ? completedFuture(requestState)
                : sendNextExecutionPayloadEnvelope(requestState));
  }

  private SafeFuture<Boolean> processNextExecutionPayloadEnvelope(final RequestState requestState) {
    // Ensure execution payloads are loaded off of the event thread
    return requestState
        .loadNextExecutionPayloadEnvelope()
        .thenCompose(
            block -> {
              requestState.decrementRemainingExecutionPayloadEnvelopes();
              return handleLoadedExecutionPayloadEnvelope(requestState, block);
            });
  }

  /** Sends the execution payload and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedExecutionPayloadEnvelope(
      final RequestState requestState,
      final Optional<SignedExecutionPayloadEnvelope> executionPayloadEnvelope) {
    return executionPayloadEnvelope
        .map(requestState::sendExecutionPayloadEnvelope)
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

  @VisibleForTesting
  class RequestState {

    private final ResponseCallback<SignedExecutionPayloadEnvelope> callback;
    private UInt64 currentSlot;
    private UInt64 remainingExecutionPayloadEnvelopes;
    private final UInt64 headSlot;
    private final NavigableMap<UInt64, ForkChoiceNode> knownNodes;

    private final AtomicInteger sentExecutionPayloadEnvelopes = new AtomicInteger(0);

    RequestState(
        final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
        final UInt64 startSlot,
        final UInt64 count,
        final UInt64 headSlot,
        final NavigableMap<UInt64, ForkChoiceNode> knownNodes) {
      this.callback = callback;
      this.currentSlot = startSlot;
      this.remainingExecutionPayloadEnvelopes = count;
      this.headSlot = headSlot;
      this.knownNodes = knownNodes;
    }

    private boolean needsMoreExecutionPayloadEnvelopes() {
      return !remainingExecutionPayloadEnvelopes.equals(ZERO);
    }

    private boolean hasReachedHeadSlot() {
      return currentSlot.isGreaterThanOrEqualTo(headSlot);
    }

    boolean isComplete() {
      return !needsMoreExecutionPayloadEnvelopes() || hasReachedHeadSlot();
    }

    SafeFuture<Void> sendExecutionPayloadEnvelope(
        final SignedExecutionPayloadEnvelope executionPayloadEnvelope) {
      return callback
          .respond(executionPayloadEnvelope)
          .thenRun(sentExecutionPayloadEnvelopes::incrementAndGet);
    }

    void decrementRemainingExecutionPayloadEnvelopes() {
      remainingExecutionPayloadEnvelopes = remainingExecutionPayloadEnvelopes.minusMinZero(1);
    }

    void incrementCurrentSlot() {
      currentSlot = currentSlot.increment();
    }

    SafeFuture<Optional<SignedExecutionPayloadEnvelope>> loadNextExecutionPayloadEnvelope() {
      final UInt64 slot = this.currentSlot;
      final ForkChoiceNode knownNode = knownNodes.get(slot);
      if (knownNode != null) {
        if (knownNode.payloadStatus() != PAYLOAD_STATUS_FULL) {
          // The payload is not part of the head chain: the next block built on the EMPTY variant,
          // or the head itself is EMPTY or PENDING because fork choice has not selected its
          // payload. Its envelope must not be served even if we hold it.
          return SafeFuture.completedFuture(Optional.empty());
        }
        // Known node whose payload is part of the chain so lookup by root
        return combinedChainDataClient
            .getExecutionPayloadByBlockRoot(knownNode.blockRoot())
            .thenApply(
                maybeExecutionPayload ->
                    maybeExecutionPayload.filter(
                        executionPayload -> executionPayload.getSlot().equals(slot)));
      } else if ((!knownNodes.isEmpty() && slot.compareTo(knownNodes.firstKey()) >= 0)
          || slot.compareTo(headSlot) > 0) {
        // Unknown root but not finalized means this is an empty slot (or payload absent)
        // Could also be because the first execution payload requested is above our head slot
        return SafeFuture.completedFuture(Optional.empty());
      } else {
        // Finalized slot: look up the canonical block then its execution payload envelope
        return combinedChainDataClient
            .getBlockAtSlotExact(slot)
            .thenCompose(
                maybeBlock ->
                    maybeBlock
                        .map(
                            block ->
                                combinedChainDataClient.getExecutionPayloadByBlockRoot(
                                    block.getRoot()))
                        .orElse(SafeFuture.completedFuture(Optional.empty())))
            .thenApply(
                maybeExecutionPayload ->
                    maybeExecutionPayload.filter(
                        executionPayload -> executionPayload.getSlot().equals(slot)));
      }
    }
  }
}
