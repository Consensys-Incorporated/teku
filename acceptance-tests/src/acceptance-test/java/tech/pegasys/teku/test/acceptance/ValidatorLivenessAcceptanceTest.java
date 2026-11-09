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

import static tech.pegasys.teku.test.acceptance.dsl.ValidatorLivenessExpectation.expectLive;
import static tech.pegasys.teku.test.acceptance.dsl.ValidatorLivenessExpectation.expectNotLive;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class ValidatorLivenessAcceptanceTest extends AcceptanceTestBase {

  private static final int NODE_VALIDATORS = 2;
  private static final int TOTAL_VALIDATORS = NODE_VALIDATORS * 2;

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private TekuBeaconNode primaryNode;
  private TekuBeaconNode secondaryNode;

  @BeforeEach
  public void setup() throws IOException {
    final UInt64 altairEpoch = UInt64.valueOf(100);
    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    primaryNode =
        createTekuBeaconNode(
            configureNode(genesisTime)
                .withAltairEpoch(altairEpoch)
                .withInteropValidators(0, NODE_VALIDATORS)
                .build());
    secondaryNode =
        createTekuBeaconNode(
            configureNode(genesisTime)
                .withAltairEpoch(altairEpoch)
                .withInteropValidators(NODE_VALIDATORS, NODE_VALIDATORS)
                .withPeers(primaryNode)
                .build());
  }

  /*
   * Primary and Secondary node, each with half of the validators
   *  - Primary is online at genesis, it's validators should be always performing duties.
   *  - no validator keys from the secondary will be seen as active in epoch 0 or 1.
   *  - Secondary is online at epoch 2, so by epoch 3 should all be performing duties.
   *  - by epoch 5, all validators should be seen as performing duties in epoch 3
   */
  @Test
  @Disabled("this test has been flaking (88% over last 100 CI) #4821")
  void shouldTrackValidatorLivenessOverEpochs() throws Exception {
    primaryNode.start();

    final int startEpoch = primaryNode.waitForEpochAtOrAbove(2);
    secondaryNode.start();
    primaryNode.checkValidatorLiveness(
        1,
        TOTAL_VALIDATORS,
        expectLive(0, NODE_VALIDATORS),
        expectNotLive(NODE_VALIDATORS, NODE_VALIDATORS));

    primaryNode.waitForEpochAtOrAbove(startEpoch + 3);
    primaryNode.checkValidatorLiveness(
        startEpoch + 1, TOTAL_VALIDATORS, expectLive(0, TOTAL_VALIDATORS));
    secondaryNode.checkValidatorLiveness(
        startEpoch + 1, TOTAL_VALIDATORS, expectLive(0, TOTAL_VALIDATORS));

    secondaryNode.stop();
    primaryNode.stop();
  }

  private TekuNodeConfigBuilder configureNode(final int genesisTime) throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withValidatorLivenessTracking()
        .withGenesisTime(genesisTime)
        .withInteropNumberOfValidators(TOTAL_VALIDATORS)
        .withRealNetwork();
  }
}
