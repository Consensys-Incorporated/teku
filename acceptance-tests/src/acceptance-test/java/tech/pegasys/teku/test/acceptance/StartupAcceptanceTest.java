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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class StartupAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldProgressChainAfterStartingFromMockGenesis() throws Exception {
    final TekuBeaconNode node = createTekuBeaconNode();
    node.start();
    node.waitForGenesis();
    node.waitForNewBlock();
  }

  @Test
  public void shouldProgressChainAfterStartingFromDisk() throws Exception {
    final TekuBeaconNode node1 = createTekuBeaconNode();
    node1.start();
    final UInt64 genesisTime = node1.getGenesisTime();
    File dataDirectory = node1.getDataDirectoryFromContainer();
    node1.stop();

    final TekuBeaconNode node2 = createTekuBeaconNode();
    node2.copyContentsToWorkingDirectory(dataDirectory);
    node2.start();
    node2.waitForGenesisTime(genesisTime);
    node2.waitForNewBlock();
  }

  @Test
  public void shouldContainSyncCommitteeAggregatesOnAltair() throws Exception {
    final TekuBeaconNode node1 =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withAltairEpoch(UInt64.ZERO).build());
    node1.start();
    node1.waitForFullSyncCommitteeAggregate();
  }

  @Test
  public void shouldFinalize() throws Exception {
    final TekuBeaconNode node1 = createTekuBeaconNode();
    node1.start();
    node1.waitForNewFinalization();
    node1.stop();
  }
}
