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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.LoggingPeerId;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespResponseLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#datacolumnsidecarsbyroot-v1">DataColumnSidecarsByRoot
 * v1</a>
 */
public class DataColumnSidecarsByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        DataColumnSidecarsByRootRequestMessage, DataColumnSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private static final int ROOT_SLOT_CACHE_SIZE = 256;

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;
  private final LRUCache<Bytes32, UInt64> blockRootSlotCache =
      LRUCache.create(ROOT_SLOT_CACHE_SIZE);

  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalDataColumnSidecarsRequestedCounter;
  private final DasReqRespLogger dasLogger;
  private final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor;

  public DataColumnSidecarsByRootMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
      final DasReqRespLogger dasLogger) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    this.dasLogger = dasLogger;
    this.dataColumnSidecarArchiveReconstructor = dataColumnSidecarArchiveReconstructor;
    this.requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "Total number of data column sidecars by root requests received",
            "status");
    this.totalDataColumnSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requested_sidecars_total",
            "Total number of data column sidecars requested in accepted data column sidecars by root requests from "
                + "peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final DataColumnSidecarsByRootRequestMessage message,
      final ResponseCallback<DataColumnSidecar> responseCallback) {
    final int requestedDataColumnSidecarsCount = message.getMaximumResponseChunks();

    final ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        dasLogger
            .getDataColumnSidecarsByRootLogger()
            .onInboundRequest(
                LoggingPeerId.fromPeerAndMaybeNodeId(
                    peer.getId().toBase58(), peer.getDiscoveryNodeId()),
                message.asList());

    final LoggingResponseCallback<DataColumnSidecar> responseCallbackWithLogging =
        new LoggingResponseCallback<>(responseCallback, responseLogger);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveDataColumnSidecarsRequest(
            responseCallbackWithLogging, requestedDataColumnSidecarsCount);

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalDataColumnSidecarsRequestedCounter.inc(requestedDataColumnSidecarsCount);

    final Set<UInt64> myCustodyColumns =
        custodyGroupCountManagerSupplier.get().getCustodyColumnIndices();

    final int messageId = dataColumnSidecarArchiveReconstructor.onRequest();
    final CompletionAwareResponseCallback<DataColumnSidecar> completionCallback =
        new CompletionAwareResponseCallback<>(responseCallbackWithLogging);
    completionCallback.onCompletion(
        () -> dataColumnSidecarArchiveReconstructor.onRequestCompleted(messageId));

    SafeFuture<Long> responseFuture = SafeFuture.completedFuture(0L);
    for (final DataColumnsByRootIdentifier byRootIdentifier : message) {
      responseFuture =
          responseFuture.thenCompose(
              sentSoFar -> {
                if (byRootIdentifier.getColumns().stream().noneMatch(myCustodyColumns::contains)) {
                  // we don't custody any of the requested columns
                  return SafeFuture.completedFuture(sentSoFar);
                }
                return resolveBlockRootSlot(byRootIdentifier.getBlockRoot())
                    .thenCompose(
                        maybeSlot ->
                            retrieveAndRespondForBlockRoot(
                                byRootIdentifier.getBlockRoot(),
                                maybeSlot,
                                byRootIdentifier.getColumns(),
                                myCustodyColumns,
                                messageId,
                                completionCallback))
                    .thenApply(sent -> sentSoFar + sent);
              });
    }

    responseFuture
        .thenAccept(
            sent -> {
              if (sent != requestedDataColumnSidecarsCount) {
                peer.adjustDataColumnSidecarsRequest(maybeRequestKey.get(), sent);
              }
              completionCallback.completeSuccessfully();
            })
        .finish(err -> handleError(err, completionCallback, "data column sidecars by root"));
  }

  private SafeFuture<Optional<DataColumnSidecar>> tryArchiveReconstruction(
      final DataColumnSlotAndIdentifier identifier, final int messageId) {
    final boolean isSuperNodePruned =
        dataColumnSidecarArchiveReconstructor.isSidecarPruned(
            identifier.slot(), identifier.columnIndex());
    if (isSuperNodePruned) {
      return combinedChainDataClient
          .getBlockByBlockRoot(identifier.blockRoot())
          .thenCompose(
              maybeBlock -> {
                if (maybeBlock.isEmpty()) {
                  LOG.debug(
                      "Cannot reconstruct pruned data column sidecar slot {} index {} blockRoot {}: "
                          + "block not available",
                      identifier.slot(),
                      identifier.columnIndex(),
                      identifier.blockRoot());
                  return SafeFuture.completedFuture(Optional.empty());
                }

                return dataColumnSidecarArchiveReconstructor
                    .reconstructDataColumnSidecar(
                        maybeBlock.get(), identifier.columnIndex(), messageId)
                    .thenPeek(
                        maybeSidecar ->
                            maybeSidecar.ifPresent(
                                sidecar ->
                                    LOG.debug(
                                        "Serving reconstructed data column sidecar slot {} index {} "
                                            + "blockRoot {} for by-root request",
                                        sidecar.getSlot(),
                                        sidecar.getIndex(),
                                        identifier.blockRoot())));
              });
    }

    return SafeFuture.completedFuture(Optional.empty());
  }

  private SafeFuture<Optional<UInt64>> resolveBlockRootSlot(final Bytes32 blockRoot) {
    final Optional<UInt64> cached = blockRootSlotCache.getCached(blockRoot);
    if (cached.isPresent()) {
      return SafeFuture.completedFuture(cached);
    }
    return combinedChainDataClient
        .getSlotByBlockRoot(blockRoot)
        .thenPeek(
            maybeSlot ->
                maybeSlot.ifPresent(
                    slot -> blockRootSlotCache.invalidateWithNewValue(blockRoot, slot)));
  }

  private SafeFuture<Long> retrieveAndRespondForBlockRoot(
      final Bytes32 blockRoot,
      final Optional<UInt64> maybeSlot,
      final List<UInt64> columns,
      final Set<UInt64> myCustodyColumns,
      final int messageId,
      final ResponseCallback<DataColumnSidecar> callback) {
    if (maybeSlot.isEmpty()) {
      return SafeFuture.completedFuture(0L);
    }
    final UInt64 slot = maybeSlot.get();
    final UInt64 requestedEpoch = spec.computeEpochAtSlot(slot);
    if (!spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        combinedChainDataClient.getStore(), requestedEpoch)) {
      // Root is outside the data column serve range; silently skip per spec
      return SafeFuture.completedFuture(0L);
    }
    SafeFuture<Long> responseFuture = SafeFuture.completedFuture(0L);
    for (final UInt64 column : columns) {
      if (!myCustodyColumns.contains(column)) {
        continue;
      }
      final DataColumnSlotAndIdentifier identifier =
          new DataColumnSlotAndIdentifier(slot, blockRoot, column);
      responseFuture =
          responseFuture.thenCompose(
              sentSoFar ->
                  retrieveAndRespondForColumn(identifier, messageId, callback)
                      .thenApply(sent -> sentSoFar + sent));
    }
    return responseFuture;
  }

  private SafeFuture<Long> retrieveAndRespondForColumn(
      final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier,
      final int messageId,
      final ResponseCallback<DataColumnSidecar> callback) {
    return retrieveDataColumnSidecar(dataColumnSlotAndIdentifier, messageId)
        .thenCompose(
            maybeSidecar ->
                maybeSidecar
                    .map(sidecar -> callback.respond(sidecar).thenApply(__ -> 1L))
                    .orElse(SafeFuture.completedFuture(0L)));
  }

  private SafeFuture<Optional<DataColumnSidecar>> retrieveDataColumnSidecar(
      final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier, final int messageId) {
    return combinedChainDataClient
        .getSidecar(dataColumnSlotAndIdentifier)
        .thenCompose(
            maybeSidecar -> {
              if (maybeSidecar.isPresent()) {
                return SafeFuture.completedFuture(maybeSidecar);
              }
              // Fallback to compacted archive reconstruction if the canonical one is not found
              return tryArchiveReconstruction(dataColumnSlotAndIdentifier, messageId);
            });
  }
}
