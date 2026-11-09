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

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

abstract class AbstractDelegatingDasDb implements DataColumnSidecarDbAccessor {
  private final DataColumnSidecarCoreDB delegateDb;

  public AbstractDelegatingDasDb(final DataColumnSidecarCoreDB delegateDb) {
    this.delegateDb = delegateDb;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return delegateDb.getSidecar(identifier);
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(final UInt64 slot) {
    return delegateDb.getColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getCustodyColumnIdentifiers(
      final UInt64 slot) {
    return delegateDb.getCustodyColumnIdentifiers(slot);
  }

  @Override
  public SafeFuture<Void> addSidecar(final DataColumnSidecar sidecar) {
    return delegateDb.addSidecar(sidecar);
  }
}
