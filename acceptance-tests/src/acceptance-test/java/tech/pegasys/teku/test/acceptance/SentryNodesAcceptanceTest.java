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

import java.io.IOException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.SentryNodesConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class SentryNodesAcceptanceTest extends AcceptanceTestBase {

  @Test
  void sentryBeaconNodesSetup() throws Exception {
    final TekuBeaconNode dutiesProviderNode = createAndStartBootstrapBeaconNode();
    final TekuBeaconNode attestationPublisherNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());
    final TekuBeaconNode blockHandlerNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());

    final SentryNodesConfig sentryNodesConfig =
        new SentryNodesConfig.Builder()
            .withDutiesProviders(dutiesProviderNode)
            .withAttestationPublisher(attestationPublisherNode)
            .withBlockHandlers(blockHandlerNode)
            .build();

    final TekuValidatorNode remoteValidator =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, 32)
                .withSentryNodes(sentryNodesConfig)
                .build());
    remoteValidator.start();

    remoteValidator.waitForDutiesRequestedFrom(dutiesProviderNode);
    remoteValidator.waitForAttestationPublishedTo(attestationPublisherNode);
    remoteValidator.waitForBlockPublishedTo(blockHandlerNode);
  }

  @Test
  void sentryBeaconNodesSetupWithFailover() throws Exception {
    final TekuBeaconNode dutiesProviderNode = createAndStartBootstrapBeaconNode();
    final TekuBeaconNode attestationPublisherNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());
    final TekuBeaconNode blockHandlerNode =
        createAndStartPeerBeaconNode(dutiesProviderNode, dutiesProviderNode.getGenesisTime());

    final SentryNodesConfig sentryNodesConfig =
        new SentryNodesConfig.Builder()
            .withDutiesProviders(dutiesProviderNode, blockHandlerNode)
            .withAttestationPublisher(attestationPublisherNode, blockHandlerNode)
            .withBlockHandlers(blockHandlerNode, attestationPublisherNode)
            .build();

    final TekuValidatorNode remoteValidator =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, 32)
                .withSentryNodes(sentryNodesConfig)
                .build());
    remoteValidator.start();

    remoteValidator.waitForDutiesRequestedFrom(dutiesProviderNode);
    remoteValidator.waitForAttestationPublishedTo(attestationPublisherNode);
    remoteValidator.waitForBlockPublishedTo(blockHandlerNode);
  }

  private TekuBeaconNode createAndStartPeerBeaconNode(
      final TekuBeaconNode dutiesProviderNode, final UInt64 genesisTime) throws Exception {
    final TekuBeaconNode blockHandlerNode =
        createLateJoiningNode(dutiesProviderNode, genesisTime.intValue());
    blockHandlerNode.start();
    return blockHandlerNode;
  }

  private TekuBeaconNode createAndStartBootstrapBeaconNode() throws Exception {
    final TekuBeaconNode dutiesProviderNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withRealNetwork()
                .withInteropNumberOfValidators(64)
                .withInteropValidators(32, 32)
                .build());
    dutiesProviderNode.start();
    return dutiesProviderNode;
  }

  private TekuBeaconNode createLateJoiningNode(
      final TekuBeaconNode primaryNode, final int genesisTime) throws IOException {
    return createTekuBeaconNode(
        TekuNodeConfigBuilder.createBeaconNode()
            .withGenesisTime(genesisTime)
            .withRealNetwork()
            .withPeers(primaryNode)
            .withInteropValidators(0, 0)
            .build());
  }
}
