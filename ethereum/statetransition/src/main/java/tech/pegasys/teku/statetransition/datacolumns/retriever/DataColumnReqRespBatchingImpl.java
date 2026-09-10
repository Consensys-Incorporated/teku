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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamHandler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DataColumnReqRespBatchingImpl implements DataColumnReqResp {
  private static final Logger LOG = LogManager.getLogger();

  private final BatchDataColumnsByRootReqResp byRootRpc;
  private final DataColumnsByRootIdentifierSchema byRootSchema;
  private final Spec spec;
  private final int cellsTargetPerRequest;

  public DataColumnReqRespBatchingImpl(
      final BatchDataColumnsByRootReqResp byRootRpc,
      final DataColumnsByRootIdentifierSchema byRootSchema,
      final Spec spec) {
    this.byRootRpc = byRootRpc;
    this.byRootSchema = byRootSchema;
    this.spec = spec;
    this.cellsTargetPerRequest =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                .getMaxRequestDataColumnSidecars()
            * 2;
  }

  private record RequestEntry(
      UInt256 nodeId,
      DataColumnSlotAndIdentifier columnIdentifier,
      SafeFuture<DataColumnSidecar> promise) {}

  private final ConcurrentLinkedQueue<RequestEntry> bufferedRequests =
      new ConcurrentLinkedQueue<>();

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      final UInt256 nodeId, final DataColumnSlotAndIdentifier columnIdentifier) {
    final RequestEntry entry = new RequestEntry(nodeId, columnIdentifier, new SafeFuture<>());
    bufferedRequests.add(entry);
    return entry.promise();
  }

  @Override
  public void flush() {
    final Map<UInt256, List<RequestEntry>> byNodes = new HashMap<>();
    RequestEntry request;
    while ((request = bufferedRequests.poll()) != null) {
      if (request.promise().isDone()) {
        // Abandoned before it ever reached the wire - typically the caller cancelled the promise
        // because the column arrived via gossip in the meantime. Sending it now would cost a
        // round trip whose response nobody consumes, and would count against the peer's request
        // budget and response score.
        LOG.trace("Dropping already-completed buffered request {}", request.columnIdentifier());
        continue;
      }
      byNodes.computeIfAbsent(request.nodeId, __ -> new ArrayList<>()).add(request);
    }
    for (final Map.Entry<UInt256, List<RequestEntry>> entry : byNodes.entrySet()) {
      flushForNode(entry.getKey(), entry.getValue());
    }
  }

  private record ByRootRequest(Bytes32 root, Set<UInt64> columns) {}

  private void flushForNode(final UInt256 nodeId, final List<RequestEntry> nodeRequests) {
    LOG.debug("Flushing requests for node {}: {} total", nodeId, nodeRequests.size());
    if (nodeRequests.isEmpty()) {
      return;
    }

    final List<ByRootRequest> byRootRequests = groupByRootRequests(nodeRequests);
    LOG.trace("Processing prepared requests for node {}: byRoot({})", nodeId, byRootRequests);

    final List<DataColumnsByRootIdentifier> byRootIdentifiers =
        byRootRequests.stream()
            .map(
                byRootRequest ->
                    byRootSchema.create(
                        byRootRequest.root(), byRootRequest.columns().stream().toList()))
            .toList();

    // for 21 max blobs it will be 1560 sidecars per request, 12+ full blocks
    // for 72 max blobs it will be 455 sidecars per request, ~3.5 full blocks
    final int maxBlobs = getMaxBlobsForRequests(nodeRequests);
    final int maxSidecarsInRequest = cellsTargetPerRequest / maxBlobs;
    final List<List<DataColumnsByRootIdentifier>> byRootBatches =
        partitionRequests(byRootIdentifiers, maxSidecarsInRequest);

    final AsyncStream<DataColumnSidecar> byRootStream =
        AsyncStream.createUnsafe(byRootBatches.iterator())
            .flatMap(byRootBatch -> byRootRpc.requestDataColumnSidecarsByRoot(nodeId, byRootBatch));

    byRootStream.consume(createResponseHandler(nodeRequests));
  }

  int getMaxBlobsForRequests(final List<RequestEntry> nodeRequests) {
    final UInt64 lastSlot = nodeRequests.getLast().columnIdentifier.slot();
    final Optional<Integer> maybeMaxBlobs = spec.getMaxBlobsPerBlockAtSlot(lastSlot);
    return maybeMaxBlobs.orElseGet(
        () ->
            SpecConfigElectra.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig())
                .getMaxBlobsPerBlock());
  }

  @VisibleForTesting
  List<List<DataColumnsByRootIdentifier>> partitionRequests(
      final List<DataColumnsByRootIdentifier> byRootIdentifiers, final int maxSize) {
    final List<List<DataColumnsByRootIdentifier>> result = new ArrayList<>();
    IdentifiersList currentBatch = new IdentifiersList(new ArrayList<>(), new AtomicInteger(0));
    for (final DataColumnsByRootIdentifier byRootIdentifier : byRootIdentifiers) {
      if (currentBatch.count().get() >= maxSize) {
        result.add(currentBatch.dataColumnsByRootIdentifiers());
        currentBatch = new IdentifiersList(new ArrayList<>(), new AtomicInteger(0));
      }
      currentBatch.dataColumnsByRootIdentifiers().add(byRootIdentifier);
      currentBatch.count().addAndGet(byRootIdentifier.getColumns().size());
    }
    if (!currentBatch.dataColumnsByRootIdentifiers().isEmpty()) {
      result.add(currentBatch.dataColumnsByRootIdentifiers());
    }

    return result;
  }

  record IdentifiersList(
      List<DataColumnsByRootIdentifier> dataColumnsByRootIdentifiers, AtomicInteger count) {
    IdentifiersList(
        final List<DataColumnsByRootIdentifier> dataColumnsByRootIdentifiers,
        final AtomicInteger count) {
      if (!dataColumnsByRootIdentifiers.isEmpty() || count.get() != 0) {
        throw new IllegalArgumentException();
      }
      this.dataColumnsByRootIdentifiers = dataColumnsByRootIdentifiers;
      this.count = count;
    }
  }

  private List<ByRootRequest> groupByRootRequests(final List<RequestEntry> nodeRequests) {
    final NavigableMap<SlotAndBlockRoot, Set<UInt64>> bySlotAndBlockRootMap = new TreeMap<>();
    nodeRequests.forEach(
        nodeRequest -> {
          final UInt64 column = nodeRequest.columnIdentifier.columnIndex();
          final SlotAndBlockRoot key = nodeRequest.columnIdentifier().getSlotAndBlockRoot();
          bySlotAndBlockRootMap.computeIfAbsent(key, k -> new HashSet<>()).add(column);
        });

    if (bySlotAndBlockRootMap.isEmpty()) {
      return Collections.emptyList();
    }

    final List<ByRootRequest> byRootRequests = new ArrayList<>();
    while (!bySlotAndBlockRootMap.isEmpty()) {
      final Map.Entry<SlotAndBlockRoot, Set<UInt64>> current =
          bySlotAndBlockRootMap.pollFirstEntry();
      byRootRequests.add(new ByRootRequest(current.getKey().getBlockRoot(), current.getValue()));
    }

    return Collections.unmodifiableList(byRootRequests);
  }

  private AsyncStreamHandler<DataColumnSidecar> createResponseHandler(
      final List<RequestEntry> nodeRequests) {
    final Map<DataColumnSlotAndIdentifier, RequestEntry> requestsByColumnId =
        nodeRequests.stream().collect(Collectors.toMap(RequestEntry::columnIdentifier, req -> req));
    return new AsyncStreamHandler<>() {

      @Override
      public SafeFuture<Boolean> onNext(final DataColumnSidecar dataColumnSidecar) {
        final DataColumnSlotAndIdentifier dataColumnIdentifier =
            DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
        final RequestEntry request = requestsByColumnId.get(dataColumnIdentifier);
        if (request == null) {
          return SafeFuture.failedFuture(
              new IllegalArgumentException(
                  "Responded data column was not requested: " + dataColumnIdentifier));
        } else {
          request.promise().complete(dataColumnSidecar);
          return TRUE_FUTURE;
        }
      }

      @Override
      public void onComplete() {
        nodeRequests.stream()
            .filter(req -> !req.promise().isDone())
            .forEach(
                req -> req.promise().completeExceptionally(new DasColumnNotAvailableException()));
      }

      @Override
      public void onError(final Throwable err) {
        nodeRequests.forEach(e -> e.promise().completeExceptionally(err));
      }
    };
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    return byRootRpc.getCurrentRequestLimit(nodeId);
  }
}
