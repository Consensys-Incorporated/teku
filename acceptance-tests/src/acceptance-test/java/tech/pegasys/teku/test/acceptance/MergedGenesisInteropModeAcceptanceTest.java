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

import static org.assertj.core.api.Fail.fail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class MergedGenesisInteropModeAcceptanceTest extends AcceptanceTestBase {

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class)
  public void startFromMergedStatePerMilestoneUsingTerminalBlockHash(
      final SpecMilestone specMilestone) throws Exception {
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      final TekuNodeConfig config =
          createTekuNodeBuilderForMilestone(specMilestone)
              .withTerminalBlockHash(
                  "0x00000000000000000000000000000000000000000000000000000000000000aa", 0)
              .withStubExecutionEngine()
              .build();

      final TekuBeaconNode node = createTekuBeaconNode(config);

      node.start();
      node.waitForNonDefaultExecutionPayload();
      node.waitForNewBlock();
    }
  }

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class)
  public void startFromMergedStatePerMilestoneUsingTotalDifficultySimulation(
      final SpecMilestone specMilestone) throws Exception {
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      final TekuNodeConfig config =
          createTekuNodeBuilderForMilestone(specMilestone).withStubExecutionEngine().build();

      final TekuBeaconNode node = createTekuBeaconNode(config);

      node.start();
      node.waitForNonDefaultExecutionPayload();
      node.waitForNewBlock();
    }
  }

  private static TekuNodeConfigBuilder createTekuNodeBuilderForMilestone(
      final SpecMilestone specMilestone) throws Exception {
    final TekuNodeConfigBuilder tekuNodeConfigBuilder =
        TekuNodeConfigBuilder.createBeaconNode()
            .withRealNetwork()
            .withNetwork("minimal")
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
            .withStartupTargetPeerCount(0)
            .withInteropNumberOfValidators(64)
            .withValidatorProposerDefaultFeeRecipient("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");

    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      tekuNodeConfigBuilder.withCapellaEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      tekuNodeConfigBuilder.withDenebEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      tekuNodeConfigBuilder.withElectraEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      tekuNodeConfigBuilder.withFuluEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      tekuNodeConfigBuilder.withGloasEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE)) {
      tekuNodeConfigBuilder.withHezeEpoch(UInt64.ZERO);
    }
    if (specMilestone.isGreaterThan(SpecMilestone.HEZE)) {
      fail("Milestone %s not used on merged genesis interop test", specMilestone);
    }

    return tekuNodeConfigBuilder;
  }
}
