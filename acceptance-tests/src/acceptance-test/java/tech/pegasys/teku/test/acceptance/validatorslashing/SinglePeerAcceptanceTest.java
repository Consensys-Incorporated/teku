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
 * Running a single node with BN/VC running in a single process. The slashing event is sent to the
 * node via the POST attester/proposer slashing POST API
 */
public class SinglePeerAcceptanceTest extends ValidatorSlashingDetectionAcceptanceTest {

  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void shouldShutDownWhenOwnedValidatorSlashed_SingleProcess_SinglePeer(
      final ValidatorSlashingDetectionAcceptanceTest.SlashingEventType slashingEventType)
      throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(10).intValue();
    final UInt64 altairEpoch = UInt64.valueOf(100);

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withRealNetwork()
                .withAltairEpoch(altairEpoch)
                .withStopVcWhenValidatorSlashedEnabled()
                .withInteropValidators(0, 32)
                .build());

    tekuNode.start();

    tekuNode.waitForEpochAtOrAbove(2);

    final int slashedValidatorIndex = 3;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInFirstEpoch =
        tekuNode.getSpec().forMilestone(SpecMilestone.ALTAIR).getSlotsPerEpoch() - 1;

    postSlashing(
        tekuNode,
        UInt64.valueOf(slotInFirstEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    tekuNode.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));
    tekuNode.waitForExit(shutdownWaitingSeconds);
  }
}
