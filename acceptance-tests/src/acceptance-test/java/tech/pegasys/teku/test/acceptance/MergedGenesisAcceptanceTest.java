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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class MergedGenesisAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";
  public static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

  private BesuNode eth1Node;
  private TekuBeaconNode tekuNode;

  @BeforeEach
  void setup() throws Exception {
    eth1Node =
        createBesuNode(
            config ->
                config
                    .withMergeSupport()
                    .withGenesisFile("besu/mergedGenesis.json")
                    .withJwtTokenAuthorization(JWT_FILE));
    eth1Node.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(4, WITHDRAWAL_ADDRESS);
    final InitialStateData initialStateData =
        createGenesisGenerator()
            .network(NETWORK_NAME)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withTotalTerminalDifficulty(0)
            .genesisExecutionPayloadHeaderSource(eth1Node::createGenesisExecutionPayload)
            .validatorKeys(validatorKeys)
            .generate();
    tekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(NETWORK_NAME)
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
                .withInitialState(initialStateData)
                .withStartupTargetPeerCount(0)
                .withReadOnlyKeystorePath(validatorKeys)
                .withValidatorProposerDefaultFeeRecipient(
                    "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
                .withExecutionEngine(eth1Node)
                .withJwtSecretFile(JWT_FILE)
                .build());
    tekuNode.start();
  }

  @Test
  void shouldHaveNonDefaultExecutionPayloadAndFinalizeAfterMergeTransition() {
    tekuNode.waitForGenesisWithNonDefaultExecutionPayload();
    tekuNode.waitForNewFinalization();
  }
}
