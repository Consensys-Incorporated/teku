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

package tech.pegasys.teku.storage.server.kvstore.schema;

import java.util.Collection;
import java.util.Map;

public interface SchemaCombinedSnapshotState extends SchemaCombined, SchemaFinalizedSnapshotState {

  @Override
  Map<String, KvStoreColumn<?, ?>> getColumnMap();

  @Override
  Map<String, KvStoreVariable<?>> getVariableMap();

  default SchemaFinalizedSnapshotStateAdapter asSchemaFinalized() {
    return new SchemaFinalizedSnapshotStateAdapter(this);
  }

  @Override
  default Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  default Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }
}
