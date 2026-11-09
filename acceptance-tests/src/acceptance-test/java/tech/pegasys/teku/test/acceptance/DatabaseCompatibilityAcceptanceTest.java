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

package tech.pegasys.teku.test.acceptance;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DatabaseCompatibilityAcceptanceTest extends AcceptanceTestBase {

  private static final String ROCKSDB_V6 = "6";

  @Test
  void shouldStartLastReleaseFromRocksDbDatabaseCreatedByLocalBuild() throws Exception {
    final TekuBeaconNode localNode =
        createTekuBeaconNode(TekuDockerVersion.LOCAL_BUILD, createRocksDbBeaconNodeConfig());
    localNode.start();
    localNode.waitForLogMessageContaining("Created RocksDB V6 Hot and Finalized database (6)");
    localNode.waitForNewBlock();
    final UInt64 genesisTime = localNode.getGenesisTime();
    final File dataDirectory = localNode.stopAndGetDataDirectoryFromContainer();

    final TekuBeaconNode lastReleaseNode =
        createTekuBeaconNode(TekuDockerVersion.LAST_RELEASE, createBeaconNodeConfig());
    lastReleaseNode.copyContentsToWorkingDirectory(dataDirectory);

    lastReleaseNode.start();
    lastReleaseNode.waitForGenesisTime(genesisTime);
    lastReleaseNode.waitForNewBlock();
  }

  private TekuNodeConfig createRocksDbBeaconNodeConfig() throws IOException {
    final TekuNodeConfig config = createBeaconNodeConfig();
    config.getConfigMap().put("Xdata-storage-create-db-version", ROCKSDB_V6);
    return config;
  }

  private TekuNodeConfig createBeaconNodeConfig() throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode().build();
  }
}
