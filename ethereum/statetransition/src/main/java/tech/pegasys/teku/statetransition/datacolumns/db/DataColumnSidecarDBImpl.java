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

package tech.pegasys.teku.statetransition.datacolumns.db;

import it.unimi.dsi.fastutil.Pair;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class DataColumnSidecarDBImpl implements DataColumnSidecarDB {
  private static final Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient combinedChainDataClient;
  private final SidecarUpdateChannel sidecarUpdateChannel;
  private final DetailLogger detailLogger = new DetailLogger();

  public DataColumnSidecarDBImpl(
      final CombinedChainDataClient combinedChainDataClient,
      final SidecarUpdateChannel sidecarUpdateChannel) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.sidecarUpdateChannel = sidecarUpdateChannel;
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return combinedChainDataClient.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return combinedChainDataClient.getSidecar(identifier);
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(final UInt64 slot) {
    return combinedChainDataClient.getDataColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getCustodyColumnIdentifiers(
      final UInt64 slot) {
    return combinedChainDataClient.getCustodyDataColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(final UInt64 slot) {
    return getFirstCustodyIncompleteSlot()
        .thenCompose(
            maybeCurrentSlot ->
                detailLogger.logNewFirstCustodyIncompleteSlot(maybeCurrentSlot, slot))
        .thenCompose(__ -> sidecarUpdateChannel.onFirstCustodyIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> addSidecar(final DataColumnSidecar sidecar) {
    return sidecarUpdateChannel
        .onNewSidecar(sidecar)
        .thenRun(() -> detailLogger.logOnNewSidecar(sidecar));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return combinedChainDataClient.getEarliestAvailableDataColumnSlot();
  }

  @Override
  public SafeFuture<Void> setEarliestAvailableDataColumnSlot(final UInt64 slot) {
    return sidecarUpdateChannel.onEarliestAvailableDataColumnSlot(slot);
  }

  private class DetailLogger {
    private final AtomicInteger addCounter = new AtomicInteger();
    private long maxAddedSlot = 0;

    private void logOnNewSidecar(final DataColumnSidecar sidecar) {
      if (!LOG.isDebugEnabled()) {
        return;
      }

      final int currentAddCounter = addCounter.incrementAndGet();
      final int slot = sidecar.getSlot().intValue();
      final long prevMaxAddedSlot;
      final long curMaxAddedSlot;
      synchronized (this) {
        prevMaxAddedSlot = maxAddedSlot;
        maxAddedSlot = slot > maxAddedSlot ? slot : maxAddedSlot;
        curMaxAddedSlot = maxAddedSlot;
      }
      if (curMaxAddedSlot > prevMaxAddedSlot) {
        final SafeFuture<UInt64> prevSlotCount =
            getColumnIdentifiers(UInt64.valueOf(curMaxAddedSlot))
                .thenApply(
                    dataColumnIdentifierStream ->
                        UInt64.valueOf(dataColumnIdentifierStream.size()));
        final SafeFuture<UInt64> finalizedSlot =
            getFirstCustodyIncompleteSlot()
                .thenApply(
                    maybeFirstIncompleteSlot ->
                        maybeFirstIncompleteSlot.orElse(UInt64.ONE).safeDecrement());
        SafeFuture.collectAll(prevSlotCount, finalizedSlot)
            .thenPeek(
                prevSlotCountFinalizedSlot ->
                    LOG.debug(
                        "DataColumnSidecarDB.addSidecar: new slot: {}, prevSlot count: {}, total added: {}, finalizedSlot: {}",
                        slot,
                        prevSlotCountFinalizedSlot.get(0),
                        currentAddCounter,
                        prevSlotCountFinalizedSlot.get(1)))
            .finishStackTrace();
      }
    }

    private SafeFuture<Void> logNewFirstCustodyIncompleteSlot(
        final Optional<UInt64> maybeCurrentSlot, final UInt64 newSlot) {
      if (!LOG.isDebugEnabled()) {
        return SafeFuture.COMPLETE;
      }

      if (maybeCurrentSlot.isEmpty() || !maybeCurrentSlot.get().equals(newSlot)) {
        return getColumnIdentifiers(newSlot)
            .thenCompose(
                newSlotColumnIdentifiers -> {
                  final long newSlotCount = newSlotColumnIdentifiers.size();
                  if (maybeCurrentSlot.isEmpty()) {
                    return SafeFuture.completedFuture(Pair.of(0L, newSlotCount));
                  } else {
                    return getColumnIdentifiers(maybeCurrentSlot.get())
                        .thenApply(
                            dataColumnIdentifierStream ->
                                Pair.of(dataColumnIdentifierStream.size(), newSlotCount));
                  }
                })
            .thenAccept(
                oldCountNewCount ->
                    LOG.debug(
                        "DataColumnSidecarDB: setFirstCustodyIncompleteSlot {} ({} cols) ~> {} ({} cols)",
                        maybeCurrentSlot.map(UInt64::toString).orElse("NA"),
                        oldCountNewCount.left(),
                        newSlot,
                        oldCountNewCount.right()));
      } else {
        return SafeFuture.COMPLETE;
      }
    }
  }
}
