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
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyrange-v1">ExecutionPayloadEnvelopesByRange</a>
 */
public class ExecutionPayloadEnvelopesByRangeMessageHandler
    extends AbstractExecutionPayloadEnvelopesMessageHandler<
        ExecutionPayloadEnvelopesByRangeRequestMessage,
        ExecutionPayloadEnvelopesByRangeMessageHandler.RequestState> {

  private final SpecConfigGloas config;

  public ExecutionPayloadEnvelopesByRangeMessageHandler(
      final Spec spec,
      final SpecConfigGloas config,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final ExecutionPayloadEnvelopesRequestLimiter requestLimiter) {
    this(
        spec,
        config,
        metricsSystem,
        combinedChainDataClient,
        requestLimiter,
        DEFAULT_UNBLINDING_BATCH_SIZE);
  }

  @VisibleForTesting
  ExecutionPayloadEnvelopesByRangeMessageHandler(
      final Spec spec,
      final SpecConfigGloas config,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final ExecutionPayloadEnvelopesRequestLimiter requestLimiter,
      final int unblindingBatchSize) {
    super(
        spec,
        combinedChainDataClient,
        metricsSystem,
        requestLimiter,
        unblindingBatchSize,
        "by_range");
    this.config = config;
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
  protected long getRequestedCount(final ExecutionPayloadEnvelopesByRangeRequestMessage message) {
    return message.getCount().longValue();
  }

  @Override
  protected boolean requiresPermit(final ExecutionPayloadEnvelopesByRangeRequestMessage message) {
    final UInt64 adjustedStartSlot = trimToServableStartSlot(message.getStartSlot());
    if (message.getCount().isZero() || adjustedStartSlot.isGreaterThan(message.getMaxSlot())) {
      return false;
    }
    // Nothing to walk beyond our head
    return combinedChainDataClient
        .getChainHead()
        .map(head -> adjustedStartSlot.isLessThanOrEqualTo(head.getSlot()))
        .orElse(true);
  }

  private UInt64 trimToServableStartSlot(final UInt64 startSlot) {
    return startSlot.max(spec.computeStartSlotAtEpoch(getMinServableEpoch()));
  }

  @Override
  protected RequestState createRequestState(
      final ExecutionPayloadEnvelopesByRangeRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 count = message.getCount();

    // Trim the requested range down to the slots we are required to serve. Retrieving an envelope
    // for a finalized block requires reading the blinded envelope from the database and unblinding
    // it with an execution layer round trip, so slots outside the servable range are dropped before
    // any of that work is done. The count is reduced along with the start slot so that we never
    // respond with envelopes from outside the range the peer asked for.
    final UInt64 adjustedStartSlot = trimToServableStartSlot(startSlot);
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

    return new RequestState(
        callback, count.longValue(), adjustedStartSlot, adjustedCount, headSlot, hotNodes);
  }

  @Override
  protected SafeFuture<Void> serve(final RequestState requestState) {
    return sendNextBatch(requestState);
  }

  /** Resolves a batch of slots to roots, loads their envelopes in one lookup and sends them. */
  private SafeFuture<Void> sendNextBatch(final RequestState requestState) {
    if (!requestState.hasMoreSlotsToWalk()) {
      return SafeFuture.COMPLETE;
    }
    if (requestState.isStreamClosed()) {
      return SafeFuture.failedFuture(new StreamClosedException());
    }
    final List<UInt64> slots = requestState.takeNextSlots(unblindingBatchSize);
    return resolveBlockRoots(slots, requestState)
        .thenCompose(
            blocks ->
                loadExecutionPayloadEnvelopes(
                    blocks,
                    SlotAndBlockRoot::getBlockRoot,
                    (block, executionPayload) ->
                        executionPayload.getSlot().equals(block.getSlot())))
        .thenCompose(requestState::sendExecutionPayloadEnvelopes)
        .thenCompose(__ -> sendNextBatch(requestState));
  }

  /** Resolves the slots in parallel, keeping slot order and skipping slots with no root. */
  private SafeFuture<List<SlotAndBlockRoot>> resolveBlockRoots(
      final List<UInt64> slots, final RequestState requestState) {
    return SafeFuture.collectAll(
            slots.stream()
                .map(
                    slot ->
                        resolveBlockRoot(slot, requestState)
                            .thenApply(
                                maybeBlockRoot ->
                                    maybeBlockRoot.map(
                                        blockRoot -> new SlotAndBlockRoot(slot, blockRoot)))))
        .thenApply(blocks -> blocks.stream().flatMap(Optional::stream).toList());
  }

  private SafeFuture<Optional<Bytes32>> resolveBlockRoot(
      final UInt64 slot, final RequestState requestState) {
    if (requestState.isFinalizedSlot(slot)) {
      return combinedChainDataClient
          .getBlockAtSlotExact(slot)
          .thenApply(maybeBlock -> maybeBlock.map(SignedBeaconBlock::getRoot));
    }
    // Hot slots: only serve payloads the head chain built on (FULL nodes)
    return completedFuture(
        requestState
            .getKnownNode(slot)
            .filter(knownNode -> knownNode.payloadStatus() == PAYLOAD_STATUS_FULL)
            .map(ForkChoiceNode::blockRoot));
  }

  static class RequestState extends ExecutionPayloadEnvelopesRequestState {

    private final UInt64 headSlot;
    private final NavigableMap<UInt64, ForkChoiceNode> knownNodes;
    private UInt64 currentSlot;
    private UInt64 remainingSlots;

    RequestState(
        final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
        final long requestedCount,
        final UInt64 startSlot,
        final UInt64 count,
        final UInt64 headSlot,
        final NavigableMap<UInt64, ForkChoiceNode> knownNodes) {
      super(callback, requestedCount);
      this.currentSlot = startSlot;
      this.remainingSlots = count;
      this.headSlot = headSlot;
      this.knownNodes = knownNodes;
    }

    Optional<ForkChoiceNode> getKnownNode(final UInt64 slot) {
      return Optional.ofNullable(knownNodes.get(slot));
    }

    boolean hasMoreSlotsToWalk() {
      return !remainingSlots.isZero() && currentSlot.isLessThanOrEqualTo(headSlot);
    }

    /** Slots below the first head chain node are read from the finalized database. */
    boolean isFinalizedSlot(final UInt64 slot) {
      return knownNodes.isEmpty() || slot.isLessThan(knownNodes.firstKey());
    }

    List<UInt64> takeNextSlots(final int maxSlots) {
      final List<UInt64> slots = new ArrayList<>();
      while (slots.size() < maxSlots && hasMoreSlotsToWalk()) {
        slots.add(currentSlot);
        currentSlot = currentSlot.increment();
        remainingSlots = remainingSlots.minusMinZero(1);
        charge(1);
      }
      return slots;
    }
  }
}
