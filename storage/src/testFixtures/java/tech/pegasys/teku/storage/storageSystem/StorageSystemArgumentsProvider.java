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

package tech.pegasys.teku.storage.storageSystem;

import static tech.pegasys.teku.storage.storageSystem.SupportedDatabaseVersionArgumentsProvider.supportedDatabaseVersions;

import it.unimi.dsi.fastutil.longs.LongList;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;

public class StorageSystemArgumentsProvider implements ArgumentsProvider {

  private final LongList stateStorageFrequencyOptions = getStateStorageFrequencies();

  protected LongList getStateStorageFrequencies() {
    return LongList.of(StorageConfiguration.DEFAULT_STORAGE_FREQUENCY);
  }

  protected List<StateStorageMode> getStorageModes() {
    return List.of(StateStorageMode.values());
  }

  protected boolean includeInMemory() {
    return true;
  }

  @Override
  public Stream<? extends Arguments> provideArguments(
      final ParameterDeclarations parameters, final ExtensionContext context) {
    final Map<String, StorageSystemSupplier> storageSystems = new HashMap<>();
    for (StateStorageMode mode : getStorageModes()) {
      for (long storageFrequency : stateStorageFrequencyOptions) {
        for (DatabaseVersion databaseVersion : supportedDatabaseVersions()) {
          if (includeInMemory()) {
            storageSystems.put(
                describeStorage(databaseVersion.name() + " (in-memory)", storageFrequency),
                new StorageSystemSupplier(
                    databaseVersion,
                    (dataPath, spec) ->
                        InMemoryStorageSystemBuilder.create()
                            .specProvider(spec)
                            .version(databaseVersion)
                            .storageMode(mode)
                            .stateStorageFrequency(storageFrequency)
                            .numberOfValidators(3)
                            .build()));
          }

          storageSystems.put(
              describeStorage(databaseVersion.name() + " (file-backed)", storageFrequency),
              new StorageSystemSupplier(
                  databaseVersion,
                  (dataPath, spec) ->
                      FileBackedStorageSystemBuilder.create()
                          .specProvider(spec)
                          .version(databaseVersion)
                          .dataDir(dataPath)
                          .storageMode(mode)
                          .stateStorageFrequency(storageFrequency)
                          .numberOfValidators(3)
                          .build()));
        }
      }
    }
    return storageSystems.entrySet().stream()
        .map((entry) -> Arguments.of("storage type: " + entry.getKey(), entry.getValue()));
  }

  private String describeStorage(final String baseName, final long storageFrequency) {
    return String.format("%s (storage freq %s)", baseName, storageFrequency);
  }

  public static class StorageSystemSupplier {
    private final DatabaseVersion version;
    private final BiFunction<Path, Spec, StorageSystem> createStorageSystem;

    public StorageSystemSupplier(
        final DatabaseVersion version,
        final BiFunction<Path, Spec, StorageSystem> createStorageSystem) {
      this.version = version;
      this.createStorageSystem = createStorageSystem;
    }

    public DatabaseVersion getDatabaseVersion() {
      return version;
    }

    public StorageSystem get(final Path dataDirectory, final Spec spec) {
      return createStorageSystem.apply(dataDirectory, spec);
    }
  }
}
