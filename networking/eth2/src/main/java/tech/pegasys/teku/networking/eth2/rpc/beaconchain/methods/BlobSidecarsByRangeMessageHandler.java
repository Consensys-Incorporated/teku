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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
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
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/p2p-interface.md#blobsidecarsbyrange-v1">BlobSidecarsByRange
 * v1</a>
 */
public class BlobSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRangeRequestMessage, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobSidecarsRequestedCounter;

  public BlobSidecarsByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "Total number of blob sidecars by range requests received",
            "status");
    totalBlobSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requested_sidecars_total",
            "Total number of blob sidecars requested in accepted blob sidecars by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobSidecarsByRangeRequestMessage request) {
    final UInt64 maxSlot;
    try {
      maxSlot = request.getMaxSlot();
    } catch (final ArithmeticException __) {
      return Optional.of(
          new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    }

    final SpecConfigDeneb specConfig =
        SpecConfigDeneb.required(spec.atSlot(getEndSlotBeforeFulu(maxSlot)).getConfig());

    final int maxRequestBlobSidecars = specConfig.getMaxRequestBlobSidecars();
    final int maxBlobsPerBlock = spec.getMaxBlobsPerBlockAtSlot(maxSlot).orElseThrow();

    final int requestedCount = calculateRequestedCount(request, maxBlobsPerBlock);

    if (requestedCount == -1 || requestedCount > maxRequestBlobSidecars) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s blob sidecars can be requested per request",
                  maxRequestBlobSidecars)));
    }

    return Optional.empty();
  }

  private UInt64 getEndSlotBeforeFulu(final UInt64 maxSlot) {
    return spec.blobSidecarsDeprecationSlot().safeDecrement().min(maxSlot);
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobSidecarsByRangeRequestMessage message,
      final ResponseCallback<BlobSidecar> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 endSlot = message.getMaxSlot();

    LOG.trace(
        "Peer {} requested {} slots of blob sidecars starting at slot {}.",
        peer.getId(),
        message.getCount(),
        startSlot);

    if (!peer.approveRequest()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    if (startSlot.isGreaterThan(spec.blobSidecarsDeprecationSlot())) {
      LOG.trace(
          "Peer {} requested {} slots of blob sidecars starting at slot {} after Fulu. BlobSidecarsByRange v1 is deprecated and the request will be ignored.",
          peer.getId(),
          message.getCount(),
          startSlot);
      requestCounter.labels("ignored").inc();
      callback.completeSuccessfully();
      return;
    }

    final UInt64 endSlotBeforeFulu = getEndSlotBeforeFulu(endSlot);
    final SpecConfigDeneb specConfig =
        SpecConfigDeneb.required(spec.atSlot(endSlotBeforeFulu).getConfig());
    final int requestedCount =
        calculateRequestedCount(
            message, spec.getMaxBlobsPerBlockAtSlot(endSlotBeforeFulu).orElseThrow());
    final Optional<RequestKey> maybeRequestKey =
        peer.approveBlobSidecarsRequest(callback, requestedCount);

    if (maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlobSidecarsRequestedCounter.inc(message.getCount().longValue());

    combinedChainDataClient
        .getEarliestAvailableBlobSidecarSlot()
        .thenCompose(
            earliestAvailableSlot -> {
              final UInt64 requestEpoch = spec.computeEpochAtSlot(startSlot);
              if (spec.isAvailabilityOfBlobSidecarsRequiredAtEpoch(
                      combinedChainDataClient.getStore(), requestEpoch)
                  && !checkBlobSidecarsAreAvailable(earliestAvailableSlot, endSlotBeforeFulu)) {
                return SafeFuture.failedFuture(
                    new ResourceUnavailableException("Requested blob sidecars are not available."));
              }

              UInt64 finalizedSlot =
                  combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);

              final UInt64 currentEpoch = spec.getCurrentEpoch(combinedChainDataClient.getStore());
              final UInt64 lowestAllowedBlobSidecarEpoch =
                  currentEpoch.minusMinZero(specConfig.getMinEpochsForBlobSidecarsRequests());
              final UInt64 adjustedStartsSlot =
                  lowestAllowedBlobSidecarEpoch.isGreaterThan(requestEpoch)
                      ? spec.computeStartSlotAtEpoch(lowestAllowedBlobSidecarEpoch)
                      : startSlot;

              final SortedMap<UInt64, Bytes32> canonicalHotRoots;
              if (endSlotBeforeFulu.isGreaterThan(finalizedSlot)) {
                final UInt64 hotSlotsCount =
                    endSlotBeforeFulu.increment().minusMinZero(adjustedStartsSlot);

                canonicalHotRoots =
                    combinedChainDataClient.getAncestorRoots(
                        adjustedStartsSlot, UInt64.ONE, hotSlotsCount);

                // refresh finalized slot to avoid race condition that can occur if we finalize just
                // before getting hot canonical roots
                finalizedSlot = combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);
              } else {
                canonicalHotRoots = ImmutableSortedMap.of();
              }

              final RequestState initialState =
                  new RequestState(
                      callback,
                      adjustedStartsSlot,
                      endSlotBeforeFulu,
                      canonicalHotRoots,
                      finalizedSlot,
                      specConfig.getMaxRequestBlobSidecars());
              if (message.getCount().isZero() || initialState.isComplete()) {
                return SafeFuture.completedFuture(initialState);
              }
              return sendBlobSidecars(initialState);
            })
        .finish(
            requestState -> {
              final int sentBlobSidecars = requestState.sentBlobSidecars.get();
              if (sentBlobSidecars != requestedCount) {
                peer.adjustBlobSidecarsRequest(maybeRequestKey.get(), sentBlobSidecars);
              }
              LOG.trace("Sent {} blob sidecars to peer {}.", sentBlobSidecars, peer.getId());
              callback.completeSuccessfully();
            },
            error -> handleError(error, callback, "blob sidecars by range"));
  }

  private int calculateRequestedCount(
      final BlobSidecarsByRangeRequestMessage message, final int maxBlobsPerBlock) {
    try {
      return message.getCount().times(maxBlobsPerBlock).intValue();
    } catch (final ArithmeticException e) {
      return -1;
    }
  }

  private boolean checkBlobSidecarsAreAvailable(
      final Optional<UInt64> earliestAvailableSidecarSlot, final UInt64 requestSlot) {
    return earliestAvailableSidecarSlot
        .map(earliestSlot -> earliestSlot.isLessThanOrEqualTo(requestSlot))
        .orElse(false);
  }

  private SafeFuture<RequestState> sendBlobSidecars(final RequestState requestState) {
    return requestState
        .loadNextBlobSidecar()
        .thenCompose(
            maybeBlobSidecar ->
                maybeBlobSidecar.map(requestState::sendBlobSidecar).orElse(SafeFuture.COMPLETE))
        .thenCompose(
            __ -> {
              if (requestState.isComplete()) {
                return SafeFuture.completedFuture(requestState);
              } else {
                return sendBlobSidecars(requestState);
              }
            });
  }

  @VisibleForTesting
  class RequestState {

    private final ResponseCallback<BlobSidecar> callback;
    private final UInt64 startSlot;
    private final UInt64 endSlot;
    private final UInt64 finalizedSlot;
    private final Map<UInt64, Bytes32> canonicalHotRoots;
    private final int maxRequestBlobSidecars;

    private final AtomicInteger sentBlobSidecars = new AtomicInteger(0);

    // since our storage stores hot and finalized blobs on the same "table", this iterator can span
    // over hot and finalized blobs
    private Optional<Iterator<SlotAndBlockRootAndBlobIndex>> blobSidecarKeysIterator =
        Optional.empty();

    RequestState(
        final ResponseCallback<BlobSidecar> callback,
        final UInt64 startSlot,
        final UInt64 endSlot,
        final Map<UInt64, Bytes32> canonicalHotRoots,
        final UInt64 finalizedSlot,
        final int maxRequestBlobSidecars) {
      this.callback = callback;
      this.startSlot = startSlot;
      this.endSlot = endSlot;
      this.finalizedSlot = finalizedSlot;
      this.canonicalHotRoots = canonicalHotRoots;
      this.maxRequestBlobSidecars = maxRequestBlobSidecars;
    }

    SafeFuture<Void> sendBlobSidecar(final BlobSidecar blobSidecar) {
      return callback.respond(blobSidecar).thenRun(sentBlobSidecars::incrementAndGet);
    }

    SafeFuture<Optional<BlobSidecar>> loadNextBlobSidecar() {
      if (blobSidecarKeysIterator.isEmpty()) {
        return combinedChainDataClient
            .getBlobSidecarKeys(startSlot, endSlot, maxRequestBlobSidecars)
            .thenCompose(
                keys -> {
                  blobSidecarKeysIterator = Optional.of(keys.iterator());
                  return getNextBlobSidecar(blobSidecarKeysIterator.get());
                });
      } else {
        return getNextBlobSidecar(blobSidecarKeysIterator.get());
      }
    }

    private SafeFuture<Optional<BlobSidecar>> getNextBlobSidecar(
        final Iterator<SlotAndBlockRootAndBlobIndex> blobSidecarKeysIterator) {
      if (blobSidecarKeysIterator.hasNext()) {
        final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex =
            blobSidecarKeysIterator.next();

        if (finalizedSlot.isGreaterThanOrEqualTo(slotAndBlockRootAndBlobIndex.getSlot())) {
          return combinedChainDataClient.getBlobSidecarByKey(slotAndBlockRootAndBlobIndex);
        }

        // not finalized, let's check if it is on canonical chain
        if (isCanonicalHotBlobSidecar(slotAndBlockRootAndBlobIndex)) {
          return combinedChainDataClient.getBlobSidecarByKey(slotAndBlockRootAndBlobIndex);
        }

        // non-canonical, try next one
        return getNextBlobSidecar(blobSidecarKeysIterator);
      }

      return SafeFuture.completedFuture(Optional.empty());
    }

    private boolean isCanonicalHotBlobSidecar(
        final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex) {
      return Optional.ofNullable(canonicalHotRoots.get(slotAndBlockRootAndBlobIndex.getSlot()))
          .map(blockRoot -> blockRoot.equals(slotAndBlockRootAndBlobIndex.getBlockRoot()))
          .orElse(false);
    }

    boolean isComplete() {
      return endSlot.isLessThan(startSlot)
          || blobSidecarKeysIterator.map(iterator -> !iterator.hasNext()).orElse(false);
    }
  }
}
