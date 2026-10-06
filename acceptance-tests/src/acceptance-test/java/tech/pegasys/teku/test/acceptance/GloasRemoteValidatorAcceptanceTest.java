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

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class GloasRemoteValidatorAcceptanceTest extends AcceptanceTestBase {
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
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withCapellaEpoch(UInt64.ZERO)
                .withDenebEpoch(UInt64.ZERO)
                .withElectraEpoch(UInt64.ZERO)
                .withFuluEpoch(UInt64.ZERO)
                .withGloasEpoch(UInt64.ONE)
                .withStubExecutionEngine()
                .withRealNetwork()
                .build());
    validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withInteropValidators(0, VALIDATOR_COUNT)
                .withBeaconNodes(beaconNode)
                .build());
  }

  @Test
  void shouldPerformDutiesWithRemoteValidator() throws Exception {
    beaconNode.start();
    validatorClient.start();

    waitForValidatorDutiesToComplete();
  }

  private void waitForValidatorDutiesToComplete() {
    validatorClient.waitForLogMessageContaining("Published block");
    validatorClient.waitForLogMessageContaining("Published attestation");
    validatorClient.waitForLogMessageContaining("Published aggregate");
    validatorClient.waitForLogMessageContaining("Published sync_signature");
    validatorClient.waitForLogMessageContaining("Published sync_contribution");
    // Gloas duties
    validatorClient.waitForLogMessageContaining("Published execution_payload");
    validatorClient.waitForLogMessageContaining("Published payload_attestation");
  }
}
