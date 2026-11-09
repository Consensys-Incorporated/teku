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

import com.google.common.io.Resources;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class OptimisticSyncPostMergeAcceptanceTest extends AcceptanceTestBase {
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");
  private static final int VALIDATORS = 64;

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private BesuNode executionNode1;
  private BesuNode executionNode2;
  private TekuBeaconNode tekuNode2;

  @BeforeEach
  void setup() throws Exception {
    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    executionNode1 =
        createBesuNode(
            config ->
                config
                    .withMergeSupport()
                    .withP2pEnabled(true)
                    .withGenesisFile("besu/preMergeGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    executionNode1.start();
    executionNode2 =
        createBesuNode(
            config ->
                config
                    .withMergeSupport()
                    .withP2pEnabled(true)
                    .withGenesisFile("besu/preMergeGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    executionNode2.start();
    executionNode2.addPeer(executionNode1);

    TekuBeaconNode tekuNode1 =
        createTekuBeaconNode(
            createBeaconNode(executionNode1, genesisTime)
                .withInteropValidators(0, VALIDATORS)
                .build());
    tekuNode1.start();
    tekuNode2 =
        createTekuBeaconNode(
            createBeaconNode(executionNode2, genesisTime)
                .withInteropValidators(0, 0)
                .withPeers(tekuNode1)
                .build());
    tekuNode2.start();
  }

  @Test
  void shouldSwitchToOptimisticSyncAfterMergeWhenExecutionEngineIsSyncing() throws Exception {
    // Reset execution client's DB after the merge and leave it without any chance to sync
    tekuNode2.waitForNonDefaultExecutionPayload();
    executionNode2.restartWithEmptyDatabase();

    tekuNode2.waitForOptimisticBlock();

    // Now make execution node sync and clarify switch from optimistic sync back to the normal
    executionNode2.addPeer(executionNode1);
    tekuNode2.waitForNonOptimisticBlock();
  }

  private TekuNodeConfigBuilder createBeaconNode(
      final BesuNode executionEngine, final int genesisTime) throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withBellatrixEpoch(UInt64.ZERO)
        .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 1)
        .withGenesisTime(genesisTime)
        .withRealNetwork()
        .withStartupTargetPeerCount(0)
        .withExecutionEngine(executionEngine)
        .withJwtSecretFile(JWT_FILE);
  }
}
