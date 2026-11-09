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

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class MultiThreadedStoreTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private static final UInt64 GENESIS_TIME = UInt64.valueOf(100);
  private static final Logger LOG = LogManager.getLogger();
  private static final Spec SPEC = TestSpecFactory.createMinimalBellatrix();
  private static final StateStorageMode STORAGE_MODE = StateStorageMode.PRUNE;
  private ChainBuilder chainBuilder;
  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private final List<File> tmpDirectories = new ArrayList<>();
  private final ExecutorService executor = Executors.newFixedThreadPool(8);
  private AtomicLong slot;

  @BeforeEach
  public void setup(@TempDir final Path tempDir) {
    LOG.info("Starting test");
    this.slot = new AtomicLong(1);
    this.chainBuilder = ChainBuilder.create(SPEC, VALIDATOR_KEYS);
    this.storageSystem =
        createStorageSystemInternal(
            tempDir.toFile(), STORAGE_MODE, StoreConfig.createDefault(), true);
    this.recentChainData = storageSystem.recentChainData();
    final SignedBlockAndState genesisBlockAndState =
        chainBuilder.generateGenesis(GENESIS_TIME, true);
    this.recentChainData.initializeFromGenesis(genesisBlockAndState.getState(), UInt64.ZERO);
  }

  @AfterEach
  public void tearDown() throws Exception {
    LOG.info("Finishing test");
    // Clean up tmp directories
    for (File tmpDirectory : tmpDirectories) {
      MoreFiles.deleteRecursively(tmpDirectory.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    }
    tmpDirectories.clear();
  }

  @RepeatedTest(100)
  @SuppressWarnings("FutureReturnValueIgnored")
  @Disabled("Made to force deadlock on DbInstance, kind of load testing. Test stalls on deadlock")
  public void testStore() {
    while (slot.get() < 1000) {
      executor.submit(createStoreTaskForSlot(slot.getAndIncrement()));
      if (slot.get() == 900) {
        tryToClose();
      }
    }
  }

  private void tryToClose() {
    try {
      LOG.info("Trying to close");
      storageSystem.close();
    } catch (Exception e) {
      LOG.error("Failed to close", e);
    }
  }

  private Runnable createStoreTaskForSlot(final long slotNumber) {
    return () -> {
      final StoreTransaction transaction = recentChainData.startStoreTransaction();
      final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(slotNumber);
      transaction.putBlockAndState(block, SPEC.calculateBlockCheckpoints(block.getState()));
      transaction.commit().finish(ex -> {});
    };
  }

  private StorageSystem createStorageSystem(
      final File tempDir,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks) {
    return FileBackedStorageSystemBuilder.create()
        .specProvider(SPEC)
        .dataDir(tempDir.toPath())
        .version(DatabaseVersion.LEVELDB2)
        .storageMode(storageMode)
        .stateRebuildTimeoutSeconds(12)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .storeNonCanonicalBlocks(storeNonCanonicalBlocks)
        .build();
  }

  private StorageSystem createStorageSystemInternal(
      final File tempDir,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks) {
    tmpDirectories.add(tempDir);
    return createStorageSystem(tempDir, storageMode, storeConfig, storeNonCanonicalBlocks);
  }
}
