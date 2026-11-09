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

package tech.pegasys.teku.spec.logic.common.util;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.LinkedHashMap;
import java.util.Map;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class EpochAttestationSchedule {
  private final Map<UInt64, SlotAttestationSchedule> slotSchedule;

  private EpochAttestationSchedule(final Map<UInt64, SlotAttestationSchedule> slotSchedule) {
    this.slotSchedule = slotSchedule;
  }

  public static Builder builder() {
    return new Builder();
  }

  public SlotAttestationSchedule atSlot(final UInt64 assignedSlot) {
    return slotSchedule.get(assignedSlot);
  }

  public static class Builder {
    private final Map<UInt64, SlotAttestationSchedule> slotSchedule = new LinkedHashMap<>();

    public EpochAttestationSchedule build() {
      return new EpochAttestationSchedule(slotSchedule);
    }

    public Builder add(
        final UInt64 slot, final UInt64 committeeIndex, final IntList validatorIndices) {
      slotSchedule.putIfAbsent(slot, new SlotAttestationSchedule(slot));
      slotSchedule.get(slot).addCommittee(committeeIndex, validatorIndices);
      return this;
    }
  }
}
