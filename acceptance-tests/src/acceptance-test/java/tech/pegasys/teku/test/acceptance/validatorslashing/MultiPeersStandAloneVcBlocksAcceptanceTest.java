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
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

/**
 * Running 2 nodes: <br>
 * - Node 1: VC/BN running in a single process <br>
 * - Node 2: Stand-alone VC with a separate BN <br>
 * The slashing event is sent to the first node via the POST attester/proposer slashing REST API. It
 * is then sent <br>
 * to the second BN within a block which sends it to it's VC via the attester/proposer slashing SSE
 * channel
 */
public class MultiPeersStandAloneVcBlocksAcceptanceTest
    extends ValidatorSlashingDetectionAcceptanceTest {
  @ParameterizedTest
  @MethodSource("getSlashingEventTypes")
  void
      shouldShutDownWhenOwnedValidatorSlashed_StandAloneVC_MultiplePeers_SlashingThroughBlock_NoSlashingEventsGossip(
          final SlashingEventType slashingEventType) throws Exception {

    final int genesisTime = timeProvider.getTimeInSeconds().plus(30).intValue();

    final TekuBeaconNode firstTekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withNetwork(network)
                .withRealNetwork()
                .withSubscribeAllSubnetsEnabled()
                .withInteropValidators(0, 32)
                .build());

    final TekuBeaconNode secondBeaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withGenesisTime(genesisTime)
                .withNetwork(network)
                .withRealNetwork()
                .withSubscribeAllSubnetsEnabled()
                .withPeers(firstTekuNode)
                .build());

    final TekuValidatorNode secondValidatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withNetwork("auto")
                .withValidatorApiEnabled()
                .withStopVcWhenValidatorSlashedEnabled()
                .withInteropValidators(32, 32)
                .withBeaconNodes(secondBeaconNode)
                .build());

    firstTekuNode.start();
    secondBeaconNode.start();
    secondValidatorClient.start();

    firstTekuNode.waitForEpochAtOrAbove(1);

    final int slashedValidatorIndex = 34;
    final BLSKeyPair slashedValidatorKeyPair = getBlsKeyPair(slashedValidatorIndex);
    final int slotInSecondEpoch = firstTekuNode.getSpec().getGenesisSpec().getSlotsPerEpoch() + 3;

    postSlashing(
        firstTekuNode,
        UInt64.valueOf(slotInSecondEpoch),
        UInt64.valueOf(slashedValidatorIndex),
        slashedValidatorKeyPair.getSecretKey(),
        slashingEventType);

    secondValidatorClient.waitForLogMessageContaining(
        String.format(slashingActionLog, slashedValidatorKeyPair.getPublicKey().toHexString()));

    secondValidatorClient.waitForExit(shutdownWaitingSeconds);

    // Make sure the BN didn't shut down
    secondBeaconNode.waitForBlockAtOrAfterSlot(4);
    // Make sure the first node didn't shut down
    firstTekuNode.waitForBlockAtOrAfterSlot(4);
    secondBeaconNode.stop();
    firstTekuNode.stop();
  }
}
