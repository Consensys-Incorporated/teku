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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public interface V4MigratableSourceDao {
  Map<String, KvStoreColumn<?, ?>> getColumnMap();

  Map<String, KvStoreVariable<?>> getVariableMap();

  <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var);

  @MustBeClosed
  <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(final KvStoreColumn<K, V> kvStoreColumn);

  <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> kvStoreColumn, K key);
}
