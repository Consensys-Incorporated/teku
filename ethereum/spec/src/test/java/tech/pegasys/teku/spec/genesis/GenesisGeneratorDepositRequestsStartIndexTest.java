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

package tech.pegasys.teku.spec.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

@TestSpecContext(milestone = {SpecMilestone.ELECTRA, SpecMilestone.FULU, SpecMilestone.GLOAS})
class GenesisGeneratorDepositRequestsStartIndexTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 16);

  private Spec spec;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
  }

  @TestTemplate
  void shouldSetDepositRequestsStartIndexAsInSpecGenesis(final SpecContext specContext) {
    final BeaconStateElectra state = BeaconStateElectra.required(generateGenesisState(spec));

    assertThat(state.getEth1DepositIndex()).isEqualTo(UInt64.valueOf(VALIDATOR_KEYS.size()));
    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      // Fulu removed the Eth1 deposit bridge (consensus-specs #4704), so genesis ends it
      assertThat(state.getDepositRequestsStartIndex()).isEqualTo(state.getEth1DepositIndex());
    } else {
      assertThat(state.getDepositRequestsStartIndex())
          .isEqualTo(UNSET_DEPOSIT_REQUESTS_START_INDEX);
    }
  }

  private static BeaconState generateGenesisState(final Spec spec) {
    final List<Deposit> deposits =
        new MockStartDepositGenerator(spec, new DepositGenerator(spec, true))
            .createDeposits(VALIDATOR_KEYS).stream().map(Deposit::new).toList();
    return spec.initializeBeaconStateFromEth1(Bytes32.ZERO, ZERO, deposits, Optional.empty());
  }
}
