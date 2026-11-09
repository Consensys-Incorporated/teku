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

package tech.pegasys.teku.validator.api;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CommitteeSubscriptionRequest {

  private final int validatorIndex;
  private final int committeeIndex;
  private final UInt64 committeesAtSlot;
  private final UInt64 slot;
  private final boolean isAggregator;

  public CommitteeSubscriptionRequest(
      final int validatorIndex,
      final int committeeIndex,
      final UInt64 committeesAtSlot,
      final UInt64 slot,
      final boolean isAggregator) {
    this.validatorIndex = validatorIndex;
    this.committeeIndex = committeeIndex;
    this.committeesAtSlot = committeesAtSlot;
    this.slot = slot;
    this.isAggregator = isAggregator;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public int getCommitteeIndex() {
    return committeeIndex;
  }

  public UInt64 getCommitteesAtSlot() {
    return committeesAtSlot;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public boolean isAggregator() {
    return isAggregator;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CommitteeSubscriptionRequest that = (CommitteeSubscriptionRequest) o;
    return validatorIndex == that.validatorIndex
        && committeeIndex == that.committeeIndex
        && isAggregator == that.isAggregator
        && Objects.equals(committeesAtSlot, that.committeesAtSlot)
        && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, committeeIndex, committeesAtSlot, slot, isAggregator);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("validatorIndex", validatorIndex)
        .add("committeeIndex", committeeIndex)
        .add("committeesAtSlot", committeesAtSlot)
        .add("slot", slot)
        .add("isAggregator", isAggregator)
        .toString();
  }
}
