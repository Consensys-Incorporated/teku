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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_FLAG;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.BUILDER_WITHDRAWAL_BYTE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

public class PredicatesGloas extends PredicatesElectra {

  public static PredicatesGloas required(final Predicates predicates) {
    return predicates
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas predicates but got " + predicates.getClass().getSimpleName()));
  }

  public PredicatesGloas(final SpecConfig specConfig) {
    super(specConfig);
  }

  public boolean isBuilderIndex(final UInt64 validatorIndex) {
    return (validatorIndex.longValue() & BUILDER_INDEX_FLAG.longValue()) != 0;
  }

  // Check if the builder at ``builder_index`` is active for the given ``state``.
  public boolean isActiveBuilder(final BeaconState state, final UInt64 builderIndex) {
    final BeaconStateGloas beaconStateGloas = BeaconStateGloas.required(state);
    if (builderIndex.isGreaterThanOrEqualTo(beaconStateGloas.getBuilders().size())) {
      return false;
    }
    final Builder builder = beaconStateGloas.getBuilders().get(builderIndex.intValue());
    // Placement in builder list is finalized
    return builder.getDepositEpoch().isLessThan(state.getFinalizedCheckpoint().getEpoch())
        // Has not initiated exit
        && builder.getWithdrawableEpoch().equals(FAR_FUTURE_EPOCH);
  }

  public boolean isBuilderWithdrawalCredential(final Bytes32 withdrawalCredentials) {
    return withdrawalCredentials.get(0) == BUILDER_WITHDRAWAL_BYTE;
  }

  @Override
  public Optional<PredicatesGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
