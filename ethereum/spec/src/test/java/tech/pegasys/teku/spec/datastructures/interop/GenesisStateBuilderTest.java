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

package tech.pegasys.teku.spec.datastructures.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class GenesisStateBuilderTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  @Test
  public void shouldCreateInitialBeaconChainState() {
    final UInt64 genesisTime = UInt64.valueOf(498294294824924924L);
    final int validatorCount = 10;

    final BeaconState initialBeaconState =
        new GenesisStateBuilder().spec(spec).genesisTime(genesisTime).addMockValidators(10).build();

    final List<BLSKeyPair> expectedKeyPairs =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);
    assertEquals(validatorCount, initialBeaconState.getValidators().size());
    assertEquals(validatorCount, initialBeaconState.getEth1Data().getDepositCount().longValue());

    final List<BLSPublicKey> actualValidatorPublicKeys =
        initialBeaconState.getValidators().stream()
            .map(Validator::getPubkeyBytes)
            .map(BLSPublicKey::fromBytesCompressed)
            .collect(Collectors.toList());
    final List<BLSPublicKey> expectedValidatorPublicKeys =
        expectedKeyPairs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList());
    assertEquals(expectedValidatorPublicKeys, actualValidatorPublicKeys);
  }
}
