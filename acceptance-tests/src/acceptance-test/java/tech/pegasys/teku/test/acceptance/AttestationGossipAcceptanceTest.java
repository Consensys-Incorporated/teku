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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class AttestationGossipAcceptanceTest extends AcceptanceTestBase {
  @Test
  public void shouldFinalizeWithTwoNodes() throws Exception {
    final TekuBeaconNode node1 =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withRealNetwork()
                .withInteropValidators(0, 32)
                .build());

    node1.start();
    final UInt64 genesisTime = node1.getGenesisTime();

    final TekuBeaconNode node2 =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime.intValue())
                .withRealNetwork()
                .withPeers(node1)
                .withInteropValidators(32, 32)
                .build());
    node2.start();

    node2.waitForAttestationBeingGossiped(32, 64);

    // Check aggregate gossip is also being received by both nodes.
    node1.waitForAggregateGossipReceived();
    node2.waitForAggregateGossipReceived();
  }
}
