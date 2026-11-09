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

package tech.pegasys.teku.reference.phase0.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public class GenesisValidityTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState state = loadStateFromSsz(testDefinition, "genesis.ssz_snappy");
    final Spec spec = testDefinition.getSpec();
    final BeaconStateUtil beaconStateUtil = spec.atEpoch(UInt64.ZERO).getBeaconStateUtil();
    final boolean expectedValidity = loadYaml(testDefinition, "is_valid.yaml", Boolean.class);
    final int activeValidatorCount =
        testDefinition.getSpec().countActiveValidators(state, SpecConfig.GENESIS_EPOCH);
    final boolean result =
        beaconStateUtil.isValidGenesisState(state.getGenesisTime(), activeValidatorCount);
    assertThat(result).isEqualTo(expectedValidity);
  }
}
