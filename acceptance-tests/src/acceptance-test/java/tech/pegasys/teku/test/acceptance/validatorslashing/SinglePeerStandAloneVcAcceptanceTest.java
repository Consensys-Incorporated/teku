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
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

/**
 * Running a stand-alone VC with a separate BN. The slashing event is sent to the BN via the POST
 * attester/proposer slashing POST API
 */
public class SinglePeerStandAloneVcAcceptanceTest extends ValidatorSlashingDetectionAcceptanceTest {

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_SinglePeer(
      final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withRealNetwork()
                .withAltairEpoch(altairEpoch)
                .build());

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withNetwork("auto")
                .withStopVcWhenValidatorSlashedEnabled()
                .withInteropValidators(0, 32)
                .withBeaconNodes(beaconNode)
                .build());

    beaconNode.start();
    validatorClient.start();

    beaconNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 3;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        beaconNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        beaconNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    validatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));
    validatorClient.waitForExit(shutdownWaitingSeconds);
    // Make sure the BN didn't shut down
    beaconNode.waitForEpochAtOrAbove(4);
    beaconNode.stop();
  }
}
