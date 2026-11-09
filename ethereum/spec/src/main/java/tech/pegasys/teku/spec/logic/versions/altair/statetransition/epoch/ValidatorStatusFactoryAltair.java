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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.AbstractValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;

public class ValidatorStatusFactoryAltair extends AbstractValidatorStatusFactory {
  private final MiscHelpersAltair miscHelpersAltair;

  public ValidatorStatusFactoryAltair(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    super(specConfig, beaconStateUtil, attestationUtil, predicates, beaconStateAccessors);
    this.miscHelpersAltair = miscHelpers;
  }

  @Override
  protected void processParticipation(
      final List<ValidatorStatus> statuses,
      final BeaconState genericState,
      final UInt64 previousEpoch,
      final UInt64 currentEpoch) {
    final BeaconStateAltair state = BeaconStateAltair.required(genericState);

    final SszList<SszByte> previousParticipation = state.getPreviousEpochParticipation();
    final SszList<SszByte> currentParticipation = state.getCurrentEpochParticipation();
    for (int i = 0; i < statuses.size(); i++) {
      final ValidatorStatus status = statuses.get(i);

      if (status.isActiveInPreviousEpoch()) {
        final byte previousParticipationFlags = previousParticipation.get(i).get();
        if (miscHelpersAltair.hasFlag(
            previousParticipationFlags, ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX)) {
          status.updatePreviousEpochSourceAttester(true);
        }
        if (miscHelpersAltair.hasFlag(
            previousParticipationFlags, ParticipationFlags.TIMELY_TARGET_FLAG_INDEX)) {
          status.updatePreviousEpochTargetAttester(true);
        }
        if (miscHelpersAltair.hasFlag(
            previousParticipationFlags, ParticipationFlags.TIMELY_HEAD_FLAG_INDEX)) {
          status.updatePreviousEpochHeadAttester(true);
        }
      }

      if (status.isActiveInCurrentEpoch()) {
        final byte currentParticipationFlags = currentParticipation.get(i).get();
        if (miscHelpersAltair.hasFlag(
            currentParticipationFlags, ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX)) {
          status.updateCurrentEpochSourceAttester(true);
        }
        if (miscHelpersAltair.hasFlag(
            currentParticipationFlags, ParticipationFlags.TIMELY_TARGET_FLAG_INDEX)) {
          status.updateCurrentEpochTargetAttester(true);
        }
        if (miscHelpersAltair.hasFlag(
            currentParticipationFlags, ParticipationFlags.TIMELY_HEAD_FLAG_INDEX)) {
          status.updateCurrentEpochHeadAttester(true);
        }
      }
    }
  }
}
