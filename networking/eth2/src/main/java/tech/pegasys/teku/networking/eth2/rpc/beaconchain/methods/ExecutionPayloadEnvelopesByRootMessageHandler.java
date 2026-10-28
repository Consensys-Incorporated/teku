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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyroot-v1">ExecutionPayloadEnvelopesByRoot</a>
 *
 * <p>Count validation is done as part of {@link ExecutionPayloadEnvelopesByRootRequestMessage}
 * schema limits
 */
public class ExecutionPayloadEnvelopesByRootMessageHandler
    extends AbstractExecutionPayloadEnvelopesMessageHandler<
        ExecutionPayloadEnvelopesByRootRequestMessage,
        ExecutionPayloadEnvelopesByRootMessageHandler.RequestState> {

  public ExecutionPayloadEnvelopesByRootMessageHandler(
      final Spec spec,
      final CombinedChainDataClient combinedChainDataClient,
      final MetricsSystem metricsSystem,
      final ExecutionPayloadEnvelopesRequestLimiter requestLimiter) {
    this(
        spec,
        combinedChainDataClient,
        metricsSystem,
        requestLimiter,
        DEFAULT_UNBLINDING_BATCH_SIZE);
  }

  @VisibleForTesting
  ExecutionPayloadEnvelopesByRootMessageHandler(
      final Spec spec,
      final CombinedChainDataClient combinedChainDataClient,
      final MetricsSystem metricsSystem,
      final ExecutionPayloadEnvelopesRequestLimiter requestLimiter,
      final int unblindingBatchSize) {
    super(
        spec,
        combinedChainDataClient,
        metricsSystem,
        requestLimiter,
        unblindingBatchSize,
        "by_root");
  }

  @Override
  protected long getRequestedCount(final ExecutionPayloadEnvelopesByRootRequestMessage message) {
    return message.size();
  }

  /** Envelopes held in memory need no EL call, so only other roots take a permit. */
  @Override
  protected boolean requiresPermit(final ExecutionPayloadEnvelopesByRootRequestMessage message) {
    for (final SszBytes32 beaconBlockRoot : message) {
      if (combinedChainDataClient
          .getRecentChainData()
          .getExecutionPayloadIfAvailable(beaconBlockRoot.get())
          .isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected RequestState createRequestState(
      final ExecutionPayloadEnvelopesByRootRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final List<Bytes32> blockRoots = new ArrayList<>();
    for (final SszBytes32 beaconBlockRoot : message) {
      blockRoots.add(beaconBlockRoot.get());
    }
    return new RequestState(callback, blockRoots, getMinServableEpoch());
  }

  @Override
  protected SafeFuture<Void> serve(final RequestState requestState) {
    return sendNextBatch(requestState);
  }

  /**
   * Filters a batch of roots by servable range, loads their envelopes in one lookup and sends them.
   */
  private SafeFuture<Void> sendNextBatch(final RequestState requestState) {
    if (!requestState.hasMoreBlockRoots()) {
      return SafeFuture.COMPLETE;
    }
    if (requestState.isStreamClosed()) {
      return SafeFuture.failedFuture(new StreamClosedException());
    }
    final UInt64 minServableEpoch = requestState.getMinServableEpoch();
    final List<Bytes32> blockRoots = requestState.takeNextBlockRoots(unblindingBatchSize);
    return selectRetrievableBlockRoots(blockRoots, minServableEpoch)
        .thenCompose(
            retrievableBlockRoots -> {
              requestState.charge(retrievableBlockRoots.size());
              return loadExecutionPayloadEnvelopes(
                  retrievableBlockRoots,
                  Function.identity(),
                  (blockRoot, executionPayload) ->
                      isWithinServableRange(
                          executionPayload.getMessage().getSlot(), minServableEpoch));
            })
        .thenCompose(requestState::sendExecutionPayloadEnvelopes)
        .thenCompose(__ -> sendNextBatch(requestState));
  }

  /**
   * Drops roots known to be outside the servable range before any EL work. Roots with an unknown
   * slot are kept and checked against the envelope instead.
   */
  private SafeFuture<List<Bytes32>> selectRetrievableBlockRoots(
      final List<Bytes32> blockRoots, final UInt64 minServableEpoch) {
    return SafeFuture.collectAll(
            blockRoots.stream()
                .map(
                    blockRoot ->
                        combinedChainDataClient
                            .getSlotByBlockRoot(blockRoot)
                            .thenApply(
                                maybeSlot ->
                                    maybeSlot.isEmpty()
                                            || isWithinServableRange(
                                                maybeSlot.get(), minServableEpoch)
                                        ? Optional.of(blockRoot)
                                        : Optional.<Bytes32>empty())))
        .thenApply(roots -> roots.stream().flatMap(Optional::stream).toList());
  }

  private boolean isWithinServableRange(final UInt64 slot, final UInt64 minServableEpoch) {
    return spec.computeEpochAtSlot(slot).isGreaterThanOrEqualTo(minServableEpoch);
  }

  static class RequestState extends ExecutionPayloadEnvelopesRequestState {

    private final List<Bytes32> blockRoots;
    private final UInt64 minServableEpoch;
    private int nextBlockRootIndex = 0;

    RequestState(
        final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
        final List<Bytes32> blockRoots,
        final UInt64 minServableEpoch) {
      super(callback, blockRoots.size());
      this.blockRoots = blockRoots;
      this.minServableEpoch = minServableEpoch;
    }

    UInt64 getMinServableEpoch() {
      return minServableEpoch;
    }

    boolean hasMoreBlockRoots() {
      return nextBlockRootIndex < blockRoots.size();
    }

    List<Bytes32> takeNextBlockRoots(final int maxBlockRoots) {
      final int endIndex = Math.min(nextBlockRootIndex + maxBlockRoots, blockRoots.size());
      final List<Bytes32> batch = List.copyOf(blockRoots.subList(nextBlockRootIndex, endIndex));
      nextBlockRootIndex = endIndex;
      return batch;
    }
  }
}
