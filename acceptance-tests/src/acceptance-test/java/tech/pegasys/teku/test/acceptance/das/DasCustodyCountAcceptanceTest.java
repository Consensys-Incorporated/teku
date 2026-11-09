/*
 * Copyright (c) 2022 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.test.acceptance.das;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuDockerVersion;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class DasCustodyCountAcceptanceTest extends AcceptanceTestBase {
  private static final String NETWORK_NAME = "swift";
  private static final Eth1Address WITHDRAWAL_ADDRESS =
      Eth1Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldSetCorrectCustodyAndSamplingCount_onValidatorNodeAfterRestart() throws Exception {
    final UInt64 currentTime = new SystemTimeProvider().getTimeInSeconds();
    final int genesisTime =
        currentTime.intValue() + 30; // genesis in 30 seconds to give node time to start

    final BesuNode besuNode = createBesuNode(genesisTime);
    besuNode.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(10, WITHDRAWAL_ADDRESS);
    final GenesisGenerator.InitialStateData initialStateData =
        createInitialState(genesisTime, besuNode, validatorKeys);

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.of(validatorKeys), false));

    tekuNode.start();
    tekuNode.waitForAllInAnyOrder(
        () ->
            tekuNode.waitForLogMessageContaining(
                "Custody group count updated to 10, because genesis validators were found."),
        () -> tekuNode.waitForLogMessageContaining("Setting cgc in ENR to: 10"),
        () -> tekuNode.waitForLogMessageContaining("Sampling group count for epoch 2: 10"),
        () -> tekuNode.waitForLogMessageContaining("Persisting custody group count of 10"),
        () -> tekuNode.waitForMilestone(SpecMilestone.FULU));
    assertThat(tekuNode.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(10));
    final File dataDirectory = tekuNode.getDataDirectoryFromContainer();
    tekuNode.stop();

    // We cannot restart same node due to docker runner limitations,
    // so copying DB and starting new node with old DB to check restart behavior
    final TekuBeaconNode tekuNode2 =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.of(validatorKeys), false));
    tekuNode2.copyContentsToWorkingDirectory(dataDirectory);

    tekuNode2.start();
    tekuNode2.waitForAllInAnyOrder(
        () -> tekuNode2.waitForLogMessageContaining("Using custody group count 10 from store"),
        () -> tekuNode2.waitForLogMessageContaining("Setting cgc in ENR to: 10"),
        () -> tekuNode2.waitForLogMessageContaining("Updating custody group count 10"),
        () ->
            tekuNode2.waitForLogMessageContaining(
                "Initialized DataColumnSidecar Custody with custody group count 10"),
        () -> tekuNode2.waitForLogMessageContaining("Initial sampling group count value: 10"));
    assertThat(tekuNode2.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(10));
  }

  @Test
  void shouldSetCorrectCustodyAndSamplingCount_onFullNodeAfterRestart() throws Exception {
    final UInt64 currentTime = new SystemTimeProvider().getTimeInSeconds();
    final int genesisTime =
        currentTime.intValue() + 30; // genesis in 30 seconds to give node time to start

    final BesuNode besuNode = createBesuNode(genesisTime);
    besuNode.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(10, WITHDRAWAL_ADDRESS);
    final GenesisGenerator.InitialStateData initialStateData =
        createInitialState(genesisTime, besuNode, validatorKeys);

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.empty(), false));

    tekuNode.start();
    tekuNode.waitForAllInAnyOrder(
        () -> tekuNode.waitForLogMessageContaining("Persisting custody group count of 4"),
        () -> tekuNode.waitForLogMessageContaining("Setting cgc in ENR to: 4"),
        () -> tekuNode.waitForLogMessageContaining("Sampling group count for epoch 1: 8"),
        () -> tekuNode.waitForMilestone(SpecMilestone.FULU));
    assertThat(tekuNode.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(4));

    final File dataDirectory = tekuNode.getDataDirectoryFromContainer();
    tekuNode.stop();

    // We cannot restart same node due to docker runner limitations,
    // so copying DB and starting new node with old DB to check restart behavior
    final TekuBeaconNode tekuNode2 =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.empty(), false));
    tekuNode2.copyContentsToWorkingDirectory(dataDirectory);

    tekuNode2.start();
    tekuNode2.waitForAllInAnyOrder(
        () -> tekuNode2.waitForLogMessageContaining("Using custody group count 4 from store"),
        () -> tekuNode2.waitForLogMessageContaining("Setting cgc in ENR to: 4"),
        () -> tekuNode2.waitForLogMessageContaining("Updating custody group count 4"),
        () ->
            tekuNode2.waitForLogMessageContaining(
                "Initialized DataColumnSidecar Custody with custody group count 4"),
        () -> tekuNode2.waitForLogMessageContaining("Initial sampling group count value: 8"));
    assertThat(tekuNode2.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(4));
  }

  @Test
  void shouldSetCorrectCustodyAndSamplingCount_onSuperNodeAfterRestart() throws Exception {
    final UInt64 currentTime = new SystemTimeProvider().getTimeInSeconds();
    final int genesisTime =
        currentTime.intValue() + 30; // genesis in 30 seconds to give node time to start

    final BesuNode besuNode = createBesuNode(genesisTime);
    besuNode.start();

    final ValidatorKeystores validatorKeys =
        createValidatorKeyGenerator().generateValidatorKeys(10, WITHDRAWAL_ADDRESS);
    final GenesisGenerator.InitialStateData initialStateData =
        createInitialState(genesisTime, besuNode, validatorKeys);

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.of(validatorKeys), true));

    tekuNode.start();
    tekuNode.waitForAllInAnyOrder(
        () -> tekuNode.waitForLogMessageContaining("Persisting custody group count of 128"),
        () -> tekuNode.waitForLogMessageContaining("Setting cgc in ENR to: 128"),
        () ->
            tekuNode.waitForLogMessageContaining(
                "Initialized DataColumnSidecar Custody with custody group count 128"),
        () ->
            tekuNode.waitForLogMessageContaining(
                "DAS Basic Sampler initialized with 128 groups to sample"),
        () ->
            tekuNode.waitForLogMessageContaining(
                "Number of required custody groups reached maximum. Activating super node reconstruction."),
        () -> tekuNode.waitForLogMessageContaining("Sampling group count for epoch 1: 128"),
        () -> tekuNode.waitForMilestone(SpecMilestone.FULU));
    assertThat(tekuNode.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(128));

    final File dataDirectory = tekuNode.getDataDirectoryFromContainer();
    tekuNode.stop();

    // We cannot restart same node due to docker runner limitations,
    // so copying DB and starting new node with old DB to check restart behavior
    final TekuBeaconNode tekuNode2 =
        createTekuBeaconNode(
            beaconNode(genesisTime, besuNode, initialStateData, Optional.of(validatorKeys), true));
    tekuNode2.copyContentsToWorkingDirectory(dataDirectory);

    tekuNode2.start();
    tekuNode2.waitForAllInAnyOrder(
        () -> tekuNode2.waitForLogMessageContaining("Using custody group count 128 from store"),
        () -> tekuNode2.waitForLogMessageContaining("Setting cgc in ENR to: 128"),
        () -> tekuNode2.waitForLogMessageContaining("Updating custody group count 128"),
        () ->
            tekuNode2.waitForLogMessageContaining(
                "Initialized DataColumnSidecar Custody with custody group count 128"),
        () ->
            tekuNode2.waitForLogMessageContaining(
                "Number of required custody groups reached maximum. Activating super node reconstruction."),
        () -> tekuNode2.waitForLogMessageContaining("Initial sampling group count value: 128"));
    assertThat(tekuNode2.getMetadataMessage(SpecMilestone.FULU).getOptionalCustodyGroupCount())
        .contains(UInt64.valueOf(128));
  }

  private BesuNode createBesuNode(final int genesisTime) {
    final int osakaTime =
        genesisTime + 4 * 2; // 4 slots, 2 seconds each (swift) - activate Prague on first slot
    final Map<String, String> genesisOverrides = Map.of("osakaTime", String.valueOf(osakaTime));

    return createBesuNode(
        BesuDockerVersion.STABLE,
        config ->
            config
                .withMergeSupport()
                .withGenesisFile("besu/osakaGenesis.json")
                .withP2pEnabled(true)
                .withJwtTokenAuthorization(JWT_FILE),
        genesisOverrides);
  }

  private GenesisGenerator.InitialStateData createInitialState(
      final int genesisTime, final BesuNode besuNode, final ValidatorKeystores validatorKeys) {
    return createGenesisGenerator()
        .network(NETWORK_NAME)
        .withGenesisTime(genesisTime)
        .genesisDelaySeconds(0)
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(UInt64.ZERO)
        .withDenebEpoch(UInt64.ZERO)
        .withElectraEpoch(UInt64.ZERO)
        .withFuluEpoch(UInt64.ONE)
        .withTotalTerminalDifficulty(0)
        .genesisExecutionPayloadHeaderSource(besuNode::createGenesisExecutionPayload)
        .validatorKeys(validatorKeys)
        .generate();
  }

  private static TekuNodeConfig beaconNode(
      final int genesisTime,
      final BesuNode besuNode,
      final GenesisGenerator.InitialStateData initialStateData,
      final Optional<ValidatorKeystores> validatorKeys,
      final boolean isSuperNode)
      throws Exception {
    final TekuNodeConfigBuilder configBuilder =
        TekuNodeConfigBuilder.createBeaconNode()
            .withInitialState(initialStateData)
            .withNetwork(NETWORK_NAME)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withCapellaEpoch(UInt64.ZERO)
            .withDenebEpoch(UInt64.ZERO)
            .withElectraEpoch(UInt64.ZERO)
            .withFuluEpoch(UInt64.ONE)
            .withTotalTerminalDifficulty(0)
            .withGenesisTime(genesisTime)
            .withExecutionEngine(besuNode)
            .withJwtSecretFile(JWT_FILE)
            .withValidatorProposerDefaultFeeRecipient("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
            .withStartupTargetPeerCount(0)
            .withRealNetwork()
            .withLogLevel("DEBUG");

    validatorKeys.ifPresent(configBuilder::withReadOnlyKeystorePath);

    if (isSuperNode) {
      configBuilder.withSubscribeAllCustodySubnetsEnabled();
    }

    return configBuilder.build();
  }
}
