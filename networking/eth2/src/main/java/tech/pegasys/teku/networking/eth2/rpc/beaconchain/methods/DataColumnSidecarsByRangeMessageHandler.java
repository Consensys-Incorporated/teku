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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.LoggingPeerId;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespResponseLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#datacolumnsidecarsbyrange-v1">DataColumnSidecarsByRange
 * v1</a>
 */
public class DataColumnSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<
        DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalDataColumnSidecarsRequestedCounter;
  private final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor;
  private final DasReqRespLogger dasLogger;

  public DataColumnSidecarsByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
      final DasReqRespLogger dasLogger) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "Total number of data column sidecars by range requests received",
            "status");
    totalDataColumnSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requested_sidecars_total",
            "Total number of data column sidecars requested in accepted blob sidecars by range requests from peers");
    this.dasLogger = dasLogger;
    this.dataColumnSidecarArchiveReconstructor = dataColumnSidecarArchiveReconstructor;
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final DataColumnSidecarsByRangeRequestMessage request) {
    final UInt64 maxSlot;
    try {
      maxSlot = request.getMaxSlot();
    } catch (final ArithmeticException __) {
      return Optional.of(
          new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    }

    final int requestedCount = calculateRequestedCount(request);
    final int maxRequestDataColumnSidecars;
    try {
      maxRequestDataColumnSidecars =
          spec.atSlot(maxSlot).miscHelpers().getMaxRequestDataColumnSidecars();
    } catch (final UnsupportedOperationException __) {
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Data column sidecars not supported for the requested slot range"));
    }
    if (requestedCount == -1 || requestedCount > maxRequestDataColumnSidecars) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              String.format(
                  "Only a maximum of %s data column sidecars can be requested per request",
                  maxRequestDataColumnSidecars)));
    }
    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final DataColumnSidecarsByRangeRequestMessage message,
      final ResponseCallback<DataColumnSidecar> callback) {
    final UInt64 startSlot = message.getStartSlot();
    final UInt64 endSlot = message.getMaxSlot();
    final List<UInt64> columns = message.getColumns();

    final ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        dasLogger
            .getDataColumnSidecarsByRangeLogger()
            .onInboundRequest(
                LoggingPeerId.fromPeerAndMaybeNodeId(
                    peer.getId().toBase58(), peer.getDiscoveryNodeId()),
                new DasReqRespLogger.ByRangeRequest(
                    message.getStartSlot(), message.getCount().intValue(), message.getColumns()));
    final LoggingResponseCallback<DataColumnSidecar> callbackWithLogging =
        new LoggingResponseCallback<>(callback, responseLogger);

    final int requestedCount = calculateRequestedCount(message);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveDataColumnSidecarsRequest(callbackWithLogging, requestedCount);

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalDataColumnSidecarsRequestedCounter.inc(message.getCount().longValue());

    UInt64 finalizedSlot = combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);

    final SortedMap<UInt64, Bytes32> canonicalHotRoots;
    if (endSlot.isGreaterThan(finalizedSlot)) {
      canonicalHotRoots =
          combinedChainDataClient.getAncestorRoots(startSlot, UInt64.ONE, message.getCount());

      // refresh finalized slot to avoid race condition that can occur if we finalize just
      // before getting hot canonical roots
      finalizedSlot = combinedChainDataClient.getFinalizedBlockSlot().orElse(UInt64.ZERO);
    } else {
      canonicalHotRoots = ImmutableSortedMap.of();
    }

    final int maxRequestDataColumnSidecars =
        spec.atSlot(endSlot).miscHelpers().getMaxRequestDataColumnSidecars();

    final int messageId = dataColumnSidecarArchiveReconstructor.onRequest();
    final CompletionAwareResponseCallback<DataColumnSidecar> completionCallback =
        new CompletionAwareResponseCallback<>(callbackWithLogging);
    completionCallback.onCompletion(
        () -> dataColumnSidecarArchiveReconstructor.onRequestCompleted(messageId));
    final RequestState initialState =
        new RequestState(
            completionCallback,
            maxRequestDataColumnSidecars,
            startSlot,
            endSlot,
            columns,
            canonicalHotRoots,
            finalizedSlot,
            messageId);

    final SafeFuture<RequestState> response;
    if (requestedCount == 0 || initialState.isComplete()) {
      response = SafeFuture.completedFuture(initialState);
    } else {
      response = sendDataColumnSidecars(initialState);
    }

    response.finish(
        requestState -> {
          final int sentDataColumnSidecars = requestState.sentDataColumnSidecars.get();
          if (sentDataColumnSidecars != requestedCount) {
            peer.adjustDataColumnSidecarsRequest(maybeRequestKey.get(), sentDataColumnSidecars);
          }
          completionCallback.completeSuccessfully();
        },
        error -> handleError(error, completionCallback, "data column sidecars by range"));
  }

  private int calculateRequestedCount(final DataColumnSidecarsByRangeRequestMessage message) {
    try {
      return message.getCount().times(message.getColumns().size()).intValue();
    } catch (final ArithmeticException e) {
      return -1;
    }
  }

  private SafeFuture<RequestState> sendDataColumnSidecars(final RequestState requestState) {
    SafeFuture<Boolean> dataColumnSidecarFuture = processNextDataColumnSidecar(requestState);
    // Avoid risk of StackOverflowException by iterating when the sidecar future is already
    // complete. Using thenCompose on the completed future would execute immediately and recurse
    // back into this method to send the next sidecar. When not already complete, thenCompose is
    // executed on a separate thread so doesn't recurse on the same stack.
    while (dataColumnSidecarFuture.isDone()
        && !dataColumnSidecarFuture.isCompletedExceptionally()) {
      if (dataColumnSidecarFuture.join()) {
        return SafeFuture.completedFuture(requestState);
      }
      dataColumnSidecarFuture = processNextDataColumnSidecar(requestState);
    }
    return dataColumnSidecarFuture.thenCompose(
        complete ->
            complete
                ? SafeFuture.completedFuture(requestState)
                : sendDataColumnSidecars(requestState));
  }

  private SafeFuture<Boolean> processNextDataColumnSidecar(final RequestState requestState) {
    return requestState
        .loadNextDataColumnSidecar()
        .thenCompose(
            maybeDataColumnSidecar ->
                handleLoadedDataColumnSidecar(requestState, maybeDataColumnSidecar));
  }

  /** Sends the data column sidecar and returns true if the request is now complete. */
  private SafeFuture<Boolean> handleLoadedDataColumnSidecar(
      final RequestState requestState, final Optional<DataColumnSidecar> maybeDataColumnSidecar) {
    return maybeDataColumnSidecar
        .map(requestState::sendDataColumnSidecar)
        .orElse(SafeFuture.COMPLETE)
        .thenApply(__ -> requestState.isComplete());
  }

  @VisibleForTesting
  class RequestState {

    private final AtomicInteger sentDataColumnSidecars = new AtomicInteger(0);
    private final ResponseCallback<DataColumnSidecar> callback;
    private final UInt64 maxRequestDataColumnSidecars;
    private final UInt64 startSlot;
    private final UInt64 endSlot;
    private final List<UInt64> columns;
    private final UInt64 finalizedSlot;
    private final Map<UInt64, Bytes32> canonicalHotRoots;
    private final int messageId;
    private final boolean maybeSuperNodePruned;

    // since our storage stores hot and finalized data columns sidecar on the same "table", this
    // iterator can span over hot and finalized data column sidecar
    private Optional<Iterator<DataColumnSlotAndIdentifier>> dataColumnSidecarKeysIterator =
        Optional.empty();

    RequestState(
        final ResponseCallback<DataColumnSidecar> callback,
        final int maxRequestDataColumnSidecars,
        final UInt64 startSlot,
        final UInt64 endSlot,
        final List<UInt64> columns,
        final Map<UInt64, Bytes32> canonicalHotRoots,
        final UInt64 finalizedSlot,
        final int messageId) {
      this.callback = callback;
      this.maxRequestDataColumnSidecars = UInt64.valueOf(maxRequestDataColumnSidecars);
      this.startSlot = startSlot;
      this.endSlot = endSlot;
      this.columns = columns;
      this.finalizedSlot = finalizedSlot;
      this.canonicalHotRoots = canonicalHotRoots;
      this.messageId = messageId;
      final UInt64 highestIndex =
          columns.stream().max(Comparator.naturalOrder()).orElse(UInt64.ZERO);
      this.maybeSuperNodePruned =
          dataColumnSidecarArchiveReconstructor.isSidecarPruned(startSlot, highestIndex)
              || dataColumnSidecarArchiveReconstructor.isSidecarPruned(endSlot, highestIndex);
    }

    SafeFuture<Void> sendDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
      return callback.respond(dataColumnSidecar).thenRun(sentDataColumnSidecars::incrementAndGet);
    }

    SafeFuture<Optional<DataColumnSidecar>> loadNextDataColumnSidecar() {
      if (dataColumnSidecarKeysIterator.isEmpty()) {
        // Intentionally cap the stored identifiers scanned before expanding pruned supernode
        // columns. A valid sparse request can ask for one reconstructed column across many
        // slots, and fully serving it may require one expensive reconstruction per slot.
        // Reconstruction by range is therefore best-effort within the normal sidecar cap.
        return combinedChainDataClient
            .getDataColumnIdentifiers(startSlot, endSlot, maxRequestDataColumnSidecars)
            .thenCompose(
                keys -> {
                  dataColumnSidecarKeysIterator =
                      Optional.of(
                          filterIdentifiers(keys).stream()
                              .limit(maxRequestDataColumnSidecars.longValue())
                              .iterator());
                  return getNextDataColumnSidecar(dataColumnSidecarKeysIterator.get());
                });
      } else {
        return getNextDataColumnSidecar(dataColumnSidecarKeysIterator.get());
      }
    }

    private List<DataColumnSlotAndIdentifier> filterIdentifiers(
        final List<DataColumnSlotAndIdentifier> dbIdentifiers) {
      final NavigableMap<UInt64, List<DataColumnSlotAndIdentifier>> canonicalSlotToColumnIds =
          dbIdentifiers.stream()
              .filter(this::isCanonicalHotOrFinalizedDataColumnSidecar)
              .collect(
                  Collectors.groupingBy(
                      DataColumnSlotAndIdentifier::slot, TreeMap::new, Collectors.toList()));
      final List<DataColumnSlotAndIdentifier> matchingKeys = new ArrayList<>();
      for (final UInt64 slot : canonicalSlotToColumnIds.navigableKeySet()) {
        if (maybeSuperNodePruned && !canonicalSlotToColumnIds.get(slot).isEmpty()) {
          // Adding all requested, we expect we either have it
          // or can reconstruct from proof archives
          final DataColumnSlotAndIdentifier first = canonicalSlotToColumnIds.get(slot).getFirst();
          columns.stream()
              .sorted()
              .forEach(
                  column ->
                      matchingKeys.add(
                          new DataColumnSlotAndIdentifier(
                              first.slot(), first.blockRoot(), column)));
        } else {
          canonicalSlotToColumnIds.get(slot).stream()
              .filter(key -> columns.contains(key.columnIndex()))
              .forEach(matchingKeys::add);
        }
      }

      return matchingKeys;
    }

    private boolean isCanonicalHotOrFinalizedDataColumnSidecar(
        final DataColumnSlotAndIdentifier columnSlotAndIdentifier) {
      return finalizedSlot.isGreaterThanOrEqualTo(columnSlotAndIdentifier.slot())
          // not finalized, let's check if it is on canonical chain
          || isCanonicalHotDataColumnSidecar(columnSlotAndIdentifier);
    }

    private boolean isComplete() {
      return endSlot.isLessThan(startSlot)
          || dataColumnSidecarKeysIterator.map(iterator -> !iterator.hasNext()).orElse(false);
    }

    private SafeFuture<Optional<DataColumnSidecar>> getNextDataColumnSidecar(
        final Iterator<DataColumnSlotAndIdentifier> dataColumnSidecarIdentifiers) {
      if (dataColumnSidecarIdentifiers.hasNext()) {
        final DataColumnSlotAndIdentifier columnSlotAndIdentifier =
            dataColumnSidecarIdentifiers.next();

        return combinedChainDataClient
            .getSidecar(columnSlotAndIdentifier)
            .thenCompose(
                maybeSidecar -> {
                  if (maybeSidecar.isPresent()) {
                    return SafeFuture.completedFuture(maybeSidecar);
                  } else {
                    return tryArchiveSidecarReconstruction(columnSlotAndIdentifier);
                  }
                });
      }

      return SafeFuture.completedFuture(Optional.empty());
    }

    private SafeFuture<Optional<DataColumnSidecar>> tryArchiveSidecarReconstruction(
        final DataColumnSlotAndIdentifier columnSlotAndIdentifier) {
      if (!maybeSuperNodePruned
          || !dataColumnSidecarArchiveReconstructor.isSidecarPruned(
              columnSlotAndIdentifier.slot(), columnSlotAndIdentifier.columnIndex())) {
        return SafeFuture.completedFuture(Optional.empty());
      }

      return combinedChainDataClient
          .getBlockAtSlotExact(columnSlotAndIdentifier.slot())
          .thenCompose(
              maybeBlock -> {
                if (maybeBlock.isEmpty()) {
                  LOG.debug(
                      "Cannot reconstruct pruned data column sidecar slot {} index {}: "
                          + "block not available",
                      columnSlotAndIdentifier.slot(),
                      columnSlotAndIdentifier.columnIndex());
                  return SafeFuture.completedFuture(Optional.empty());
                }

                return dataColumnSidecarArchiveReconstructor
                    .reconstructDataColumnSidecar(
                        maybeBlock.get(), columnSlotAndIdentifier.columnIndex(), messageId)
                    .thenPeek(
                        maybeSidecar ->
                            maybeSidecar.ifPresent(
                                sidecar ->
                                    LOG.debug(
                                        "Serving reconstructed data column sidecar slot {} index {} "
                                            + "blockRoot {} for by-range request",
                                        sidecar.getSlot(),
                                        sidecar.getIndex(),
                                        columnSlotAndIdentifier.blockRoot())));
              });
    }

    private boolean isCanonicalHotDataColumnSidecar(
        final DataColumnSlotAndIdentifier columnSlotAndIdentifier) {
      return Optional.ofNullable(canonicalHotRoots.get(columnSlotAndIdentifier.slot()))
          .map(blockRoot -> blockRoot.equals(columnSlotAndIdentifier.blockRoot()))
          .orElse(false);
    }
  }
}
