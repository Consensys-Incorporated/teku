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

package tech.pegasys.teku.storage.client.compatibility;

import static tech.pegasys.teku.storage.client.compatibility.CompatibilityTestData.copyTestDataTo;
import static tech.pegasys.teku.storage.client.compatibility.CompatibilityTestData.populateDatabaseWithTestData;
import static tech.pegasys.teku.storage.client.compatibility.CompatibilityTestData.verifyDatabaseContentMatches;

import it.unimi.dsi.fastutil.longs.LongList;
import java.io.File;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider.StorageSystemSupplier;

public class DatabaseCompatibilityTest {

  private static Spec spec;
  private static StorageSystem expectedData;

  @TempDir File dataDirectory;

  @BeforeAll
  static void createExpected() {
    spec = TestSpecFactory.createDefault();
    final StorageSystem expectedSystem = InMemoryStorageSystemBuilder.buildDefault(3, spec);
    populateDatabaseWithTestData(expectedSystem);
    expectedData = expectedSystem;
  }

  @AfterAll
  static void cleanUpExpected() throws Exception {
    if (expectedData != null) {
      expectedData.close();
      expectedData = null;
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(AllStatesStorageSystemArgumentsProvider.class)
  void shouldReadContentFromExistingDatabase(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    copyTestDataTo(storageSystemSupplier.getDatabaseVersion(), dataDirectory);
    loadDatabaseAndCheckContent(storageSystemSupplier);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(AllStatesStorageSystemArgumentsProvider.class)
  @Disabled // Only run when needing to create new database files.
  void recreateDatabaseFiles(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    createDatabase(storageSystemSupplier);

    // Sanity test
    loadDatabaseAndCheckContent(storageSystemSupplier);

    final File testDataDir =
        new File(
            "src/integration-test/resources/"
                + DatabaseCompatibilityTest.class.getPackageName().replace('.', '/'),
            storageSystemSupplier.getDatabaseVersion().getValue());
    if (testDataDir.exists()) {
      FileUtils.deleteDirectory(testDataDir);
    }
    FileUtils.copyDirectory(dataDirectory, testDataDir);
  }

  private void loadDatabaseAndCheckContent(final StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    final StorageSystem newSystem = storageSystemSupplier.get(dataDirectory.toPath(), spec);
    try {
      newSystem
          .chainUpdater()
          .updateBestBlock(expectedData.chainBuilder().getLatestBlockAndState());
      verifyDatabaseContentMatches(expectedData, newSystem);
    } finally {
      newSystem.database().close();
    }
  }

  private void createDatabase(final StorageSystemSupplier storageSystemSupplier) throws Exception {
    final StorageSystem storageSystem = storageSystemSupplier.get(dataDirectory.toPath(), spec);

    populateDatabaseWithTestData(storageSystem);
    storageSystem.close();
  }

  static class AllStatesStorageSystemArgumentsProvider extends StorageSystemArgumentsProvider {
    @Override
    protected LongList getStateStorageFrequencies() {
      return LongList.of(1);
    }

    @Override
    protected List<StateStorageMode> getStorageModes() {
      return List.of(StateStorageMode.ARCHIVE);
    }

    @Override
    protected boolean includeInMemory() {
      return false;
    }
  }
}
