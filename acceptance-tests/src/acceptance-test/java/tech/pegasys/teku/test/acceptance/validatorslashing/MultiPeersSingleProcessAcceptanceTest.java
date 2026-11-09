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

package tech.pegasys.teku.test.acceptance.validatorslashing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

/**
 * Running 2 nodes with VC/BN running in a single process The slashing event is sent to the first
 * node via the POST attester/proposer slashing REST API. It's then <br>
 * received by the second node (running the slashed validator)
 */
public class MultiPeersSingleProcessAcceptanceTest
    extends ValidatorSlashingDetectionAcceptanceTest {

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_MultiplePeers(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();

    final TekuBeaconNode firstTekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withRealNetwork()
                .withInteropValidators(0, 32)
                .build());

    firstTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final TekuBeaconNode secondTekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withRealNetwork()
                .withStopVcWhenValidatorSlashedEnabled()
                .withInteropValidators(32, 32)
                .withPeers(firstTekuNode)
                .build());

    secondTekuNode.start();

    firstTekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        firstTekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondTekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));
    secondTekuNode.waitForExit(shutdownWaitingSeconds);
    // Make sure the first node didn't shut down
    firstTekuNode.waitForEpochAtOrAbove(4);
    firstTekuNode.stop();
  }
}
