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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;

class SyncTarget {
  private final TargetChain targetChain;
  private final boolean finalizedSync;
  private final boolean speculativeSync;

  private SyncTarget(
      final TargetChain targetChain, final boolean finalizedSync, final boolean speculativeSync) {
    this.targetChain = targetChain;
    this.finalizedSync = finalizedSync;
    this.speculativeSync = speculativeSync;
  }

  public static SyncTarget finalizedTarget(final TargetChain targetChain) {
    return new SyncTarget(targetChain, true, false);
  }

  public static SyncTarget nonfinalizedTarget(final TargetChain targetChain) {
    return new SyncTarget(targetChain, false, false);
  }

  public static SyncTarget speculativeTarget(final TargetChain targetChain) {
    return new SyncTarget(targetChain, false, true);
  }

  public TargetChain getTargetChain() {
    return targetChain;
  }

  public boolean isFinalizedSync() {
    return finalizedSync;
  }

  public boolean isNonfinalizedSync() {
    return !finalizedSync && !speculativeSync;
  }

  public boolean isSpeculativeSync() {
    return speculativeSync;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncTarget that = (SyncTarget) o;
    return finalizedSync == that.finalizedSync
        && speculativeSync == that.speculativeSync
        && Objects.equals(targetChain, that.targetChain);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetChain, finalizedSync, speculativeSync);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("targetChain", targetChain)
        .add("finalizedSync", finalizedSync)
        .add("speculativeSync", speculativeSync)
        .toString();
  }
}
