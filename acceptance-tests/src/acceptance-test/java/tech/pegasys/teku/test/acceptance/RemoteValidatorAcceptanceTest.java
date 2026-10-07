/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder.DEFAULT_NETWORK_NAME;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorAcceptanceTest extends AcceptanceTestBase {
  private static final int VALIDATOR_COUNT = 8;
  private TekuBeaconNode beaconNode;
  private TekuValidatorNode validatorClient;

  @BeforeEach
  public void setup() throws IOException {
    beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withInteropNumberOfValidators(VALIDATOR_COUNT)
                .withInteropValidators(0, 0)
                .build());
    validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodes(beaconNode)
                .build());
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidator() throws Exception {
    beaconNode.start();
    validatorClient.start();

    waitForValidatorDutiesToComplete();
  }

  @Test
  void shouldCreateAttestationsWithRemoteValidatorStartingFirst() throws Exception {
    // if the validator starts first with network auto, it'll spin until it gets spec
    // so here we'll explicitly define the network in use.
    validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withNetwork(DEFAULT_NETWORK_NAME)
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodes(beaconNode)
                .withLogLevel("DEBUG")
                .build());
    validatorClient.start();
    validatorClient.waitForLogMessageContaining(
        "Error while connecting to beacon node event stream");

    beaconNode.start();

    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
  }

  @Test
  void shouldFailoverWhenPrimaryBeaconNodeGoesDown() throws Exception {
    final TekuBeaconNode failoverBeaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withInteropNumberOfValidators(VALIDATOR_COUNT)
                .withInteropValidators(0, 0)
                .withPeers(beaconNode)
                .build());

    beaconNode.start();
    failoverBeaconNode.start();

    validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodes(beaconNode, failoverBeaconNode)
                .build());

    validatorClient.start();

    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();

    beaconNode.stop(false);

    validatorClient.waitForLogMessageContaining(
        "Switching to failover beacon node for event streaming");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();

    // primary beacon node recovers
    beaconNode.start();

    validatorClient.waitForLogMessageContaining(
        "Switching back to the primary beacon node for event streaming");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
  }

  @Test
  void shouldPerformDutiesIfPrimaryBeaconNodeIsDownOnStartup() throws Exception {
    // creating a primary beacon node which we would never start
    final TekuBeaconNode primaryBeaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withInteropNumberOfValidators(VALIDATOR_COUNT)
                .withInteropValidators(0, 0)
                .withPeers(beaconNode)
                .build());

    beaconNode.start();

    validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodes(primaryBeaconNode, beaconNode)
                .build());

    validatorClient.start();

    validatorClient.waitForLogMessageContaining(
        "The primary beacon node is NOT ready to accept requests");
    validatorClient.waitForLogMessageContaining(
        "Switching to failover beacon node for event streaming");
    waitForSuccessfulEventStreamConnection();
    waitForValidatorDutiesToComplete();
  }

  private void waitForSuccessfulEventStreamConnection() {
    validatorClient.waitForLogMessageContaining(
        "Successfully connected to beacon node event stream");
  }

  private void waitForValidatorDutiesToComplete() {
    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
    validatorClient.waitForLogMessageContaining("Published sync_signature");
    validatorClient.waitForLogMessageContaining("Published sync_contribution");
    assertHeadEventsWereReceived();
  }

  /**
   * Duties are also driven by the slot timer, so they complete even when the event stream delivers
   * nothing at all. The validator client reports a stream which was opened and then failed without
   * delivering any head event, and on a healthy connection that must never happen.
   */
  private void assertHeadEventsWereReceived() {
    assertThat(
            validatorClient.getFilteredOutputContaining(
                "No head events were received from the beacon node event stream"))
        .isEmpty();
  }
}
