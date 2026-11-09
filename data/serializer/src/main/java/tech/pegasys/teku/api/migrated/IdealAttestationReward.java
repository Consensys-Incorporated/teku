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

package tech.pegasys.teku.api.migrated;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class IdealAttestationReward {

  private final UInt64 effectiveBalance;
  private long head = 0L;
  private long target = 0L;
  private long source = 0L;
  private final Optional<UInt64> inclusionDelay = Optional.empty();
  private long inactivity = 0L;

  public IdealAttestationReward(final UInt64 effectiveBalance) {
    this.effectiveBalance = effectiveBalance;
  }

  public UInt64 getEffectiveBalance() {
    return effectiveBalance;
  }

  public long getHead() {
    return head;
  }

  public long getTarget() {
    return target;
  }

  public long getSource() {
    return source;
  }

  public Optional<UInt64> getInclusionDelay() {
    return inclusionDelay;
  }

  public long getInactivity() {
    return inactivity;
  }

  public void addHead(final long head) {
    this.head += head;
  }

  public void addTarget(final long target) {
    this.target += target;
  }

  public void addSource(final long source) {
    this.source += source;
  }

  public void addInactivity(final long inactivity) {
    this.inactivity += inactivity;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IdealAttestationReward that = (IdealAttestationReward) o;
    return effectiveBalance.equals(that.effectiveBalance)
        && head == that.head
        && target == that.target
        && source == that.source
        && inclusionDelay.equals(that.inclusionDelay)
        && inactivity == that.inactivity;
  }

  @Override
  public int hashCode() {
    return Objects.hash(effectiveBalance, head, target, source, inclusionDelay, inactivity);
  }
}
