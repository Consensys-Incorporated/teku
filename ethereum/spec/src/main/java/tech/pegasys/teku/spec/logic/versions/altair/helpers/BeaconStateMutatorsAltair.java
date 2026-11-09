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

package tech.pegasys.teku.spec.logic.versions.altair.helpers;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class BeaconStateMutatorsAltair extends BeaconStateMutators {
  private final SpecConfigAltair specConfigAltair;

  public BeaconStateMutatorsAltair(
      final SpecConfigAltair specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors);
    this.specConfigAltair = specConfig;
  }

  @Override
  public void addValidatorToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    super.addValidatorToRegistry(state, pubkey, withdrawalCredentials, amount);
    final MutableBeaconStateAltair stateAltair = MutableBeaconStateAltair.required(state);

    stateAltair.getPreviousEpochParticipation().append(SszByte.ZERO);
    stateAltair.getCurrentEpochParticipation().append(SszByte.ZERO);
    stateAltair.getInactivityScores().append(SszUInt64.ZERO);
  }

  @Override
  protected UInt64 calculateProposerReward(final UInt64 whistleblowerReward) {
    return whistleblowerReward.times(PROPOSER_WEIGHT).dividedBy(WEIGHT_DENOMINATOR);
  }

  @Override
  protected int getMinSlashingPenaltyQuotient() {
    return specConfigAltair.getMinSlashingPenaltyQuotientAltair();
  }

  @Override
  public void slashValidator(
      final MutableBeaconState state,
      final int slashedIndex,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    super.slashValidator(state, slashedIndex, validatorExitContextSupplier);
    BeaconStateCache.getTransitionCaches(state)
        .getProgressiveTotalBalances()
        .onSlashing(state, slashedIndex);
  }
}
