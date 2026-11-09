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

package tech.pegasys.teku.spec.logic.common;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class ProcessorTestHelper {

  protected final Spec spec = createSpec();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  protected final SpecVersion genesisSpec = spec.getGenesisSpec();
  protected final SpecConfig specConfig = genesisSpec.getConfig();

  protected abstract Spec createSpec();

  protected BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  protected BeaconState createBeaconState(final UInt64 amount, final Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  protected BeaconState createBeaconState(
      final boolean addToList, final UInt64 amount, final Validator knownValidator) {
    return spec.getGenesisSpec()
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      specConfig.getGenesisForkVersion(),
                      specConfig.getGenesisForkVersion(),
                      SpecConfig.GENESIS_EPOCH));

              List<Validator> validatorList =
                  new ArrayList<>(
                      List.of(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()));
              List<UInt64> balanceList =
                  new ArrayList<>(
                      List.of(
                          dataStructureUtil.randomUInt64(),
                          dataStructureUtil.randomUInt64(),
                          dataStructureUtil.randomUInt64()));

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState.getValidators().appendAll(validatorList);
              beaconState.getBalances().appendAllElements(balanceList);
            });
  }

  protected Validator makeValidator(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials) {
    return makeValidator(
        pubkey, withdrawalCredentials, SpecConfig.FAR_FUTURE_EPOCH, SpecConfig.FAR_FUTURE_EPOCH);
  }

  protected Validator makeValidator(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 exitEpoch,
      final UInt64 withdrawableEpoch) {
    return new Validator(
        pubkey,
        withdrawalCredentials,
        specConfig.getMaxEffectiveBalance(),
        false,
        SpecConfig.FAR_FUTURE_EPOCH,
        SpecConfig.FAR_FUTURE_EPOCH,
        exitEpoch,
        withdrawableEpoch);
  }
}
