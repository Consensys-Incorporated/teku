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
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.Web3SignerNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class CapellaRemoteSignerAcceptanceTest extends AcceptanceTestBase {

  private static final String NETWORK_NAME = "swift";
  public static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void capellaWithRemoteSigner() throws Exception {
    final UInt64 currentTime = new SystemTimeProvider().getTimeInSeconds();
    final int genesisTime =
        currentTime.intValue() + 45; // genesis needs added time for nodes to startup

    final Web3SignerNode web3SignerNode =
        createWeb3SignerNode(
            config ->
                config
                    .withNetwork(NETWORK_NAME)
                    .withAltairEpoch(UInt64.ZERO)
                    .withBellatrixEpoch(UInt64.ZERO)
                    .withCapellaEpoch(UInt64.ZERO));

    web3SignerNode.start();
    final ValidatorKeysApi signerApi = web3SignerNode.getValidatorKeysApi();
    final BesuNode besuNode = createBesuNode(genesisTime);
    besuNode.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(4, WITHDRAWAL_ADDRESS);

    signerApi.addLocalValidatorsAndExpect(validatorKeys, "imported");
    signerApi.assertLocalValidatorListing(validatorKeys.getPublicKeys());

    final InitialStateData initialStateData =
        createGenesisGenerator()
            .network(NETWORK_NAME)
            .withGenesisTime(genesisTime)
            .genesisDelaySeconds(0)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withCapellaEpoch(UInt64.ZERO)
            .withTotalTerminalDifficulty(0)
            .genesisExecutionPayloadHeaderSource(besuNode::createGenesisExecutionPayload)
            .validatorKeys(validatorKeys)
            .generate();

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            beaconNode(
                genesisTime, besuNode, initialStateData, web3SignerNode.getValidatorRestApiUrl()));

    tekuNode.start();

    tekuNode.waitForNextEpoch();
    tekuNode.waitForNewBlock();
    tekuNode.waitForFullSyncCommitteeAggregate();
  }

  private BesuNode createBesuNode(final int genesisTime) {
    final int shanghai =
        genesisTime + 2; // 4 slots, 2 seconds each (swift) - activate Prague on first slot
    final Map<String, String> genesisOverrides = Map.of("shanghaiTime", String.valueOf(shanghai));

    return createBesuNode(
        BesuDockerVersion.STABLE,
        config ->
            config
                .withMergeSupport()
                .withGenesisFile("besu/mergedGenesis.json")
                .withP2pEnabled(true)
                .withJwtTokenAuthorization(JWT_FILE),
        genesisOverrides);
  }

  private static TekuNodeConfig beaconNode(
      final int genesisTime,
      final BesuNode besuNode,
      final InitialStateData initialStateData,
      final String signerUrl)
      throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withInitialState(initialStateData)
        .withInteropModeDisabled()
        .withNetwork(NETWORK_NAME)
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(UInt64.ZERO)
        .withTotalTerminalDifficulty(0)
        .withGenesisTime(genesisTime)
        .withExecutionEngine(besuNode)
        .withJwtSecretFile(JWT_FILE)
        .withExternalSignerUrl(signerUrl)
        .withExternalSignerPublicKeys("external-signer")
        .withValidatorProposerDefaultFeeRecipient("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
        .withStartupTargetPeerCount(0)
        .withRealNetwork()
        .build();
  }
}
