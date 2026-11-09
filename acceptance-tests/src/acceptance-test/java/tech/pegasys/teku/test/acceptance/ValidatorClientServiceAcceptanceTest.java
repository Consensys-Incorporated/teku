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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator;
import tech.pegasys.teku.test.acceptance.dsl.SimpleHttpClient;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorClientServiceAcceptanceTest extends AcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnBeaconNode() throws Exception {
    final TekuNodeConfig tekuNodeConfig =
        TekuNodeConfigBuilder.createBeaconNode()
            .withInteropValidators(0, 0)
            .withExitWhenNoValidatorKeysEnabled(true)
            .build();
    final TekuBeaconNode beaconNode = createTekuBeaconNode(tekuNodeConfig);
    beaconNode.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
  }

  @Test
  void shouldFailWithNoValidatorKeysSourceProvidedOnValidatorClient() throws Exception {
    final TekuBeaconNode beaconNode = createTekuBeaconNode();

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .build());
    beaconNode.start();
    validatorClient.startWithFailure(
        "No validator keys source provided, should provide local or remote keys otherwise enable the key-manager"
            + " api to start the validator client");
    beaconNode.stop();
  }

  @Test
  void bn_shouldFailIfValidatorKeyLocked(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores initialKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withWritableKeystorePath(initialKeystores, tempDir)
                .withInitialState(genesis)
                .build());

    beaconNode.startWithFailure("FATAL - Failed to load keystore", 20);
  }

  @Test
  void vc_shouldFailIfValidatorKeyLocked(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores initialKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withInitialState(genesis).build());
    beaconNode.start();

    final TekuValidatorNode validatorNode =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withBeaconNodes(beaconNode)
                .withNetwork("auto")
                .withWritableKeystorePath(initialKeystores, tempDir)
                .build());

    validatorNode.startWithFailure("FATAL - Failed to load keystore", 20);
  }

  @Test
  void bn_shouldFailIfCannotLockKeys(@TempDir final Path tempDir) throws Exception {
    final String networkName = "swift";
    // keys aren't locked, but we're on readonly filesystem, so will cause unchecked io exception
    final ValidatorKeystores initialKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2, false);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(initialKeystores, tempDir)
                .build());

    beaconNode.startWithFailure("FATAL - Please check the logs for details.");
  }

  @Test
  void bn_shouldFailIfCannotLockKeysUnhandledException() throws Exception {
    final String networkName = "swift";
    // keys aren't locked, but we have no access to write
    final ValidatorKeystores initialKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2, true);

    final GenesisGenerator.InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(initialKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(initialKeystores)
                .withValidatorKeystoreLockingEnabled(true)
                .build());

    beaconNode.startWithFailure("FATAL - Failed to load keystore, error Access Denied", 20);
  }

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabledOnValidatorClient() throws Exception {
    final TekuBeaconNode beaconNode = createTekuBeaconNode();

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withValidatorApiEnabled()
                .withExitWhenNoValidatorKeysEnabled(true)
                .build());
    beaconNode.start();
    validatorClient.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true");
    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnBeaconNode() throws Exception {
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withExitWhenNoValidatorKeysEnabled(false)
                .withInteropValidators(0, 0)
                .build());
    beaconNode.start();

    beaconNode.waitForEpochAtOrAbove(1);

    beaconNode.stop();
  }

  @Test
  void shouldNotFailWithNoValidatorKeysWhenExitOptionDisabledOnValidatorClient() throws Exception {
    final TekuBeaconNode beaconNode = createTekuBeaconNode();
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withExitWhenNoValidatorKeysEnabled(false)
                .build());
    beaconNode.start();
    validatorClient.start();
    beaconNode.waitForEpochAtOrAbove(1);

    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();
    api.assertLocalValidatorListing(Collections.emptyList());
    validatorClient.stop();
    beaconNode.stop();
  }

  @Test
  void shouldStartValidatorApiWithoutSslAndAccessData() throws Exception {
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withExitWhenNoValidatorKeysEnabled(false)
                .withValidatorApiNoSsl(true)
                .withSpecifiedBearerToken("admin")
                .withInteropValidators(0, 8)
                .build());
    beaconNode.start();
    beaconNode.waitForLogMessageContaining("UNSAFE");
    try {
      final SimpleHttpClient client = new SimpleHttpClient();
      final String result =
          client.get(
              beaconNode.getValidatorApiUrl(),
              "/eth/v1/keystores",
              Map.of("Authorization", "Bearer admin"));
      assertThat(result).contains("validating_pubkey");
    } catch (AssertionError ex) {
      fail("Failed to read response from keystores");
    }
  }

  @Test
  void shouldStartValidatorApiWithoutSslAndRequireCorrectBearer() throws Exception {
    boolean caught = false;
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withExitWhenNoValidatorKeysEnabled(false)
                .withValidatorApiNoSsl(true)
                .withSpecifiedBearerToken("admin")
                .withInteropValidators(0, 8)
                .build());
    beaconNode.start();
    beaconNode.waitForLogMessageContaining("UNSAFE");
    try {
      final SimpleHttpClient client = new SimpleHttpClient();
      // without a bearer token this will fail
      client.get(beaconNode.getValidatorApiUrl(), "/eth/v1/keystores");
    } catch (AssertionError ex) {
      caught = true;
      LOG.debug(ex.getMessage());
      assertThat(ex.getMessage()).contains("401");
    }
    assertTrue(caught);
  }
}
