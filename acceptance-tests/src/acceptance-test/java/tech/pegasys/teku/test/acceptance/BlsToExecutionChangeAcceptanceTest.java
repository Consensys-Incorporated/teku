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

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class BlsToExecutionChangeAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldUpdateWithdrawalCredentials() throws Exception {
    final int validatorIndex = 0;
    // Generating the same keypair that is used for interop validator index = 0
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 1);
    final BLSKeyPair validatorKeyPair = blsKeyPairs.get(validatorIndex);
    final Eth1Address executionAddress =
        Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73");
    final UInt64 capellaActivationEpoch = UInt64.ONE;

    final TekuBeaconNode primaryNode = createPrimaryNode(executionAddress, capellaActivationEpoch);
    primaryNode.start();
    primaryNode.waitForNextEpoch();
    primaryNode.startEventListener(EventType.bls_to_execution_change);

    final TekuBeaconNode lateJoiningNode =
        createLateJoiningNode(capellaActivationEpoch, primaryNode, primaryNode.getGenesisTime());
    lateJoiningNode.start();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
    lateJoiningNode.startEventListener(EventType.bls_to_execution_change);

    lateJoiningNode.submitBlsToExecutionChange(validatorIndex, validatorKeyPair, executionAddress);
    lateJoiningNode.waitForValidatorWithCredentials(validatorIndex, executionAddress);

    primaryNode.waitForBlsToExecutionChangeEventForValidator(0);
    lateJoiningNode.waitForBlsToExecutionChangeEventForValidator(0);
  }

  private TekuBeaconNode createPrimaryNode(
      final Eth1Address executionAddress, final UInt64 capellaActivationEpoch) throws IOException {
    return createTekuBeaconNode(
        beaconNodeWithMilestones(capellaActivationEpoch)
            .withStartupTargetPeerCount(0)
            .withValidatorProposerDefaultFeeRecipient(executionAddress.toHexString())
            .build());
  }

  private TekuBeaconNode createLateJoiningNode(
      final UInt64 capellaActivationEpoch,
      final TekuBeaconNode primaryNode,
      final UInt64 genesisTime)
      throws IOException {
    return createTekuBeaconNode(
        beaconNodeWithMilestones(capellaActivationEpoch)
            .withPeers(primaryNode)
            .withGenesisTime(genesisTime.intValue())
            .withInteropValidators(0, 0)
            .build());
  }

  private static TekuNodeConfigBuilder beaconNodeWithMilestones(final UInt64 capellaActivationEpoch)
      throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withRealNetwork()
        .withAltairEpoch(UInt64.ZERO)
        .withBellatrixEpoch(UInt64.ZERO)
        .withCapellaEpoch(capellaActivationEpoch)
        .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
        .withStubExecutionEngine(DEFAULT_EL_GENESIS_HASH);
  }
}
