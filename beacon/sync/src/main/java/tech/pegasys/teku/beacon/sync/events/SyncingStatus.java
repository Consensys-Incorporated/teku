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

package tech.pegasys.teku.beacon.sync.events;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncingStatus {
  private final boolean syncing;
  private final UInt64 currentSlot;
  private final Optional<UInt64> startingSlot;
  private final Optional<UInt64> highestSlot;

  public SyncingStatus(
      final boolean syncing,
      final UInt64 currentSlot,
      final Optional<UInt64> startingSlot,
      final Optional<UInt64> highestSlot) {
    this.syncing = syncing;
    this.currentSlot = currentSlot;
    this.startingSlot = startingSlot;
    this.highestSlot = highestSlot;
  }

  public SyncingStatus(
      final boolean syncing,
      final UInt64 currentSlot,
      final UInt64 startingSlot,
      final UInt64 highestSlot) {
    this.syncing = syncing;
    this.currentSlot = currentSlot;
    this.startingSlot = Optional.ofNullable(startingSlot);
    this.highestSlot = Optional.ofNullable(highestSlot);
  }

  public SyncingStatus(final boolean syncing, final UInt64 currentSlot) {
    this.syncing = syncing;
    this.currentSlot = currentSlot;
    this.startingSlot = Optional.empty();
    this.highestSlot = Optional.empty();
  }

  public UInt64 getCurrentSlot() {
    return this.currentSlot;
  }

  public Optional<UInt64> getStartingSlot() {
    return startingSlot;
  }

  public Optional<UInt64> getHighestSlot() {
    return highestSlot;
  }

  public boolean isSyncing() {
    return syncing;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncingStatus that = (SyncingStatus) o;
    return syncing == that.syncing
        && Objects.equals(currentSlot, that.currentSlot)
        && Objects.equals(startingSlot, that.startingSlot)
        && Objects.equals(highestSlot, that.highestSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(syncing, currentSlot, startingSlot, highestSlot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("syncing", syncing)
        .add("currentSlot", currentSlot)
        .add("startingSlot", startingSlot)
        .add("highestSlot", highestSlot)
        .toString();
  }
}
