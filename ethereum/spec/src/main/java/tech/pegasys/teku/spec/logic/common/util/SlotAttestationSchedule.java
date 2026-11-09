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
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlotAttestationSchedule {
  private final UInt64 slot;

  private final List<IntList> committees = new ArrayList<>();

  private UInt64 maxCommittee = UInt64.ZERO;

  private int currentCommittee = 0;
  private int committeeIndex = 0;

  public SlotAttestationSchedule(final UInt64 slot) {
    this.slot = slot;
  }

  public void addCommittee(final UInt64 committee, final IntList validatorIndices) {
    maxCommittee = maxCommittee.max(committee);
    committees.add(committee.intValue(), validatorIndices);
  }

  public IntList getCommittee(final UInt64 committee) {
    return committees.get(committee.intValue());
  }

  public UInt64 getLastCommittee() {
    return maxCommittee;
  }

  public int countCommittees() {
    return committees.size();
  }

  public boolean isDone() {
    return maxCommittee.isLessThan(currentCommittee);
  }

  public UInt64 getCurrentCommittee() {
    return UInt64.valueOf(currentCommittee);
  }

  public Integer getIndexIntoCommittee() {
    return committeeIndex;
  }

  public void next() {
    committeeIndex++;
    if (committeeIndex >= committees.get(currentCommittee).size()) {
      committeeIndex = 0;
      currentCommittee++;
    }
  }

  public UInt64 getSlot() {
    return slot;
  }
}
