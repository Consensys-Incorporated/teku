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

package tech.pegasys.teku.storage.server;

import static java.util.Collections.emptyList;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

public class DataColumnSidecarNetworkRetrieverImpl implements DataColumnSidecarNetworkRetriever {
  private static final Logger LOG = LogManager.getLogger();

  final Function<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> retriever;
  final Runnable retrieverFlusher;
  final Function<DataColumnSidecar, SafeFuture<Void>> dbWriter;
  final Duration retrievalTimeout;

  public DataColumnSidecarNetworkRetrieverImpl(
      final Function<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> retriever,
      final Runnable retrieverFlusher,
      final Function<DataColumnSidecar, SafeFuture<Void>> dbWriter,
      final Duration retrievalTimeout) {
    this.retriever = retriever;
    this.retrieverFlusher = retrieverFlusher;
    this.dbWriter = dbWriter;
    this.retrievalTimeout = retrievalTimeout;
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> retrieveDataColumnSidecars(
      final List<DataColumnSlotAndIdentifier> requiredIdentifiers) {

    final List<SafeFuture<DataColumnSidecar>> requests =
        requiredIdentifiers.stream()
            .map(
                columnId -> {
                  var rpcRequest = retriever.apply(columnId);
                  rpcRequest
                      .thenCompose(dbWriter)
                      .finish(
                          error ->
                              LOG.debug(
                                  "Failed to write data column sidecars for column {}",
                                  columnId,
                                  error));
                  return rpcRequest;
                })
            .toList();

    if (!requests.isEmpty()) {
      retrieverFlusher.run();
    }

    LOG.debug("Retrieving {} additional sidecars from RPC", requests.size());

    return SafeFuture.collectAll(requests.stream())
        .orTimeout(retrievalTimeout)
        .exceptionally(
            error -> {
              LOG.debug(
                  "Error while retrieving missing columns over RPC: {}",
                  () -> ExceptionUtil.getMessageOrSimpleName(error));
              requests.forEach(request -> request.cancel(true));
              return emptyList();
            });
  }
}
