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

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;

/**
 * In order to cover all possible validator slashing scenarios, this acceptance test runs different
 * combinations: <br>
 * - Single Process: VC/BN running in a single process. In this case there is no SSE. <br>
 * - Stand-Alone VC: VC/BN running in a separate processes and communicating through the REST APIs
 * and SSE. In this case the slashing event is sent from the BN to the VC through SSE. <br>
 * - Single Peer: No network, the slashing event is directly received by the running node. <br>
 * - Multi Peers: Multiple running nodes, the slashing event is received by a first node through the
 * PostAttesterSlashing or PostProposerSlashing REST APIs and then sent to the concerned node either
 * through gossip or within a block. <br>
 * - No Blocks: the slashing event is not received by the slashed node within a block but rather
 * through the proposer_slashing or attester_slashing p2p gossip topics. <br>
 * - No Gossip: the slashing event is not received by the slashed node through the proposer_slashing
 * or attester_slashing p2p gossip topics but rather within a block. <br>
 */
public class ValidatorSlashingDetectionAcceptanceTest extends AcceptanceTestBase {

  final SystemTimeProvider timeProvider = new SystemTimeProvider();
  final String network = "swift";
  final String slashingActionLog =
      "Validator slashing detection is enabled and validator(s) with public key(s) %s detected as slashed. "
          + "Shutting down...";
  final int shutdownWaitingSeconds = 90;

  enum SlashingEventType {
    PROPOSER_SLASHING,
    ATTESTER_SLASHING
  }

  static Stream<Arguments> getSlashingEventTypes() {
    return Stream.of(SlashingEventType.values()).map(Arguments::of);
  }

  BLSKeyPair getBlsKeyPair(final int slashedValidatorIndex) {
    final List<BLSKeyPair> blsKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, slashedValidatorIndex + 1);
    return blsKeyPairs.get(slashedValidatorIndex);
  }

  void postSlashing(
      final TekuBeaconNode tekuNode,
      final UInt64 slashingSlot,
      final UInt64 slashedIndex,
      final BLSSecretKey slashedValidatorSecretKey,
      final SlashingEventType slashingEventType)
      throws IOException {
    switch (slashingEventType) {
      case ATTESTER_SLASHING ->
          tekuNode.postAttesterSlashing(slashingSlot, slashedIndex, slashedValidatorSecretKey);
      case PROPOSER_SLASHING ->
          tekuNode.postProposerSlashing(slashingSlot, slashedIndex, slashedValidatorSecretKey);
    }
  }
}
