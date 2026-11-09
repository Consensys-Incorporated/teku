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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DataColumnSidecarRetrieverStub implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger();

  public record RetrieveRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> future) {}

  public List<RetrieveRequest> requests = new ArrayList<>();
  private final Map<DataColumnSlotAndIdentifier, DataColumnSidecar> readySidecars = new HashMap<>();
  public List<DataColumnSidecar> validatedSidecars = new ArrayList<>();
  public boolean flushed = false;

  public void addReadyColumnSidecar(final DataColumnSidecar sidecar) {
    final DataColumnSlotAndIdentifier colId = DataColumnSlotAndIdentifier.fromDataColumn(sidecar);
    LOG.debug("ADD sidecar {}", colId);
    readySidecars.put(colId, sidecar);
    requests.stream()
        .filter(req -> req.columnId.equals(colId))
        .forEach(req -> req.future.complete(sidecar));
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final RetrieveRequest request = new RetrieveRequest(columnId, new SafeFuture<>());
    LOG.debug("RETRIEVE sidecar {}", columnId);
    requests.add(request);
    final DataColumnSidecar maybeSidecar = readySidecars.get(columnId);
    if (maybeSidecar != null) {
      request.future.complete(maybeSidecar);
    }
    return request.future;
  }

  @Override
  public void flush() {
    LOG.debug("FLUSH");
    flushed = true;
  }

  @Override
  public void onNewValidatedSidecar(
      final DataColumnSidecar sidecar, final RemoteOrigin remoteOrigin) {
    final DataColumnSlotAndIdentifier columnId =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);
    LOG.debug("onNewValidatedSidecar {}", columnId);
    validatedSidecars.add(sidecar);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
