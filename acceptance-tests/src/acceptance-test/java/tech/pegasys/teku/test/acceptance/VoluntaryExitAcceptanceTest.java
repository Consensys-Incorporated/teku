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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuVoluntaryExit;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeyGenerator;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class VoluntaryExitAcceptanceTest extends AcceptanceTestBase {

  private static final String DOCKER_IMAGE_LINE_SEPARATOR = "\n"; // Unix line separator

  @Test
  void shouldChangeValidatorStatusAfterSubmittingVoluntaryExit() throws Exception {
    final String networkName = "swift";

    final ValidatorKeyGenerator depositSender = createValidatorKeyGenerator();
    final ValidatorKeystores validatorKeysToExit = depositSender.generateValidatorKeys(4);
    // network of 8 validators (4 of them will exit)
    final ValidatorKeystores validatorKeys =
        ValidatorKeystores.add(validatorKeysToExit, depositSender.generateValidatorKeys(4));
    // 1 unknown key to the network
    final ValidatorKeystores unknownKeys = depositSender.generateValidatorKeys(1);

    final InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(validatorKeys).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withInitialState(genesis).build());

    final TekuVoluntaryExit voluntaryExitProcessFailing =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeysToExit);

    final TekuVoluntaryExit voluntaryExitProcessSuccessful =
        createVoluntaryExit(config -> config.withBeaconNode(beaconNode))
            .withValidatorKeystores(validatorKeysToExit)
            .withValidatorKeystores(unknownKeys);

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withReadOnlyKeystorePath(validatorKeys)
                .build());

    beaconNode.start();
    validatorClient.start();

    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");

    beaconNode.waitForEpochAtOrAbove(1);
    voluntaryExitProcessFailing.start();

    beaconNode.waitForEpochAtOrAbove(3);
    voluntaryExitProcessSuccessful.start();
    validatorClient.waitForLogMessageContaining("has changed status from");
    voluntaryExitProcessFailing.waitForExit();
    voluntaryExitProcessSuccessful.waitForExit();
    final List<Integer> validatorIds =
        Arrays.stream(
                voluntaryExitProcessFailing.getLoggedErrors().split(DOCKER_IMAGE_LINE_SEPARATOR))
            .filter(s -> s.contains("Validator cannot exit until epoch 3"))
            .map(s -> Integer.parseInt(s.substring(19, 20)))
            .collect(Collectors.toList());
    assertThat(validatorIds.size()).isEqualTo(4);
    assertThat(validatorIds).containsExactlyInAnyOrder(0, 1, 2, 3);
    assertThat(voluntaryExitProcessSuccessful.getLoggedErrors())
        .contains("Validator not found: " + unknownKeys.getPublicKeys().get(0).toString());
  }
}
