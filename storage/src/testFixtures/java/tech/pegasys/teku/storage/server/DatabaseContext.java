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

import java.nio.file.Path;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;

public class DatabaseContext {
  private final DatabaseVersion databaseVersion;

  private final boolean inMemoryStorage;

  public DatabaseContext(final DatabaseVersion databaseVersion, final boolean inMemoryStorage) {
    this.databaseVersion = databaseVersion;
    this.inMemoryStorage = inMemoryStorage;
  }

  public DatabaseVersion getDatabaseVersion() {
    return databaseVersion;
  }

  public boolean isInMemoryStorage() {
    return inMemoryStorage;
  }

  public StorageSystem createInMemoryStorage(
      final Spec spec,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks) {
    return InMemoryStorageSystemBuilder.create()
        .specProvider(spec)
        .version(getDatabaseVersion())
        .storageMode(storageMode)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .storeNonCanonicalBlocks(storeNonCanonicalBlocks)
        .build();
  }

  public StorageSystem createFileBasedStorage(
      final Spec spec,
      final Path tmpDir,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks) {

    return FileBackedStorageSystemBuilder.create()
        .specProvider(spec)
        .dataDir(tmpDir)
        .version(getDatabaseVersion())
        .storageMode(storageMode)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .storeNonCanonicalBlocks(storeNonCanonicalBlocks)
        .build();
  }
}
