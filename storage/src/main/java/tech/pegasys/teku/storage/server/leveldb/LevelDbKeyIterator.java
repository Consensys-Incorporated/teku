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

package tech.pegasys.teku.storage.server.leveldb;

import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.isFromColumn;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.removeKeyPrefix;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;

public class LevelDbKeyIterator<K, V> implements Iterator<byte[]> {

  private final LevelDbInstance dbInstance;
  private final CustomDBIterator iterator;
  private final KvStoreColumn<K, V> column;
  private final byte[] lastKey;

  public LevelDbKeyIterator(
      final LevelDbInstance dbInstance,
      final CustomDBIterator iterator,
      final KvStoreColumn<K, V> column,
      final byte[] lastKey) {
    this.dbInstance = dbInstance;
    this.iterator = iterator;
    this.column = column;
    this.lastKey = lastKey;
  }

  @Override
  public boolean hasNext() {
    synchronized (dbInstance) {
      dbInstance.assertOpen();
      return iterator.hasNext() && isValidKey();
    }
  }

  private boolean isValidKey() {
    final byte[] nextKey = iterator.peekNextKey();
    return isFromColumn(column, nextKey) && Arrays.compareUnsigned(nextKey, lastKey) <= 0;
  }

  @Override
  public byte[] next() {
    synchronized (dbInstance) {
      dbInstance.assertOpen();
      return removeKeyPrefix(column, iterator.nextKey());
    }
  }

  public Stream<byte[]> toStream() {
    final Spliterator<byte[]> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false);
  }
}
