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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

public class ValidatorsUtilGloas extends ValidatorsUtil {

  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public ValidatorsUtilGloas(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors);
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  /**
   * Returns the slot during the requested epoch in which the validator with index `validator_index`
   * is a member of the PTC. Returns None if no assignment is found.
   */
  @Override
  public Optional<UInt64> getPtcAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    final UInt64 endSlotExclusive = startSlot.plus(specConfig.getSlotsPerEpoch());

    for (UInt64 slot = startSlot; slot.isLessThan(endSlotExclusive); slot = slot.plus(UInt64.ONE)) {
      if (beaconStateAccessorsGloas.getPtc(state, slot).contains(validatorIndex)) {
        return Optional.of(slot);
      }
    }
    return Optional.empty();
  }

  @Override
  public Int2ObjectMap<UInt64> getValidatorIndexToPtcAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<UInt64> ptcAssignment = new Int2ObjectOpenHashMap<>();

    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);

    UInt64.range(startSlot, startSlot.plus(specConfig.getSlotsPerEpoch()))
        .forEach(
            slot -> {
              final IntList ptc = beaconStateAccessorsGloas.getPtc(state, slot);
              ptc.intStream()
                  .distinct()
                  .forEach(validatorIndex -> ptcAssignment.put(validatorIndex, slot));
            });

    return ptcAssignment;
  }
}
