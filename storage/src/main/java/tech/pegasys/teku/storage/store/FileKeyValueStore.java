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

package tech.pegasys.teku.storage.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;

/**
 * The key-value store implementation with String keys and Bytes values which stores each entry in a
 * separate file named {@code <key>.dat} in the specified directory
 *
 * <p>This implementation is thread-safe
 */
public class FileKeyValueStore implements KeyValueStore<String, Bytes> {

  private final SyncDataAccessor syncDataAccessor;
  private final Path dataDir;
  private final ConcurrentMap<String, Object> keyMutexes = new ConcurrentHashMap<>();

  public FileKeyValueStore(final Path dataDir) {
    this.dataDir = dataDir;
    this.syncDataAccessor = SyncDataAccessor.create(dataDir);
  }

  private Object keyMutex(final String key) {
    // there supposed to be a very limited number of keys so
    // we don't clean up the map for the sake of simplicity
    return keyMutexes.computeIfAbsent(key, __ -> new Object());
  }

  @Override
  public void put(final @NotNull String key, final @NotNull Bytes value) {
    Path file = dataDir.resolve(key + ".dat");
    try {
      synchronized (keyMutex(key)) {
        syncDataAccessor.syncedWrite(file, value);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error writing file: " + file, e);
    }
  }

  @Override
  public void remove(@NotNull final String key) {
    Path file = dataDir.resolve(key + ".dat");
    synchronized (keyMutex(key)) {
      file.toFile().delete();
    }
  }

  @Override
  public Optional<Bytes> get(@NotNull final String key) {
    Path file = dataDir.resolve(key + ".dat");
    try {
      synchronized (keyMutex(key)) {
        return syncDataAccessor.read(file);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error reading file: " + file, e);
    }
  }
}
