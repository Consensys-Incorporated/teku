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

package tech.pegasys.teku.statetransition.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

@SuppressWarnings("UnsynchronizedOverridesSynchronized")
public class NoOpPendingPool<T> extends PendingPool<T> {

  public NoOpPendingPool(
      final SettableLabelledGauge sizeGauge,
      final String itemType,
      final Spec spec,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxItems,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> requiredBlockRootsFunction,
      final Function<T, UInt64> targetSlotFunction) {
    super(
        sizeGauge,
        itemType,
        spec,
        historicalSlotTolerance,
        futureSlotTolerance,
        maxItems,
        hashTreeRootFunction,
        requiredBlockRootsFunction,
        targetSlotFunction);
  }

  @Override
  public void initMetricsLabel() {}

  @Override
  protected boolean shouldIgnoreItemAtSlot(final UInt64 slot) {
    return false;
  }

  @Override
  protected UInt64 getCurrentSlot() {
    return UInt64.ZERO;
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {}

  @Override
  public void prune() {}

  @Override
  public void onSlot(final UInt64 slot) {}

  @Override
  protected void prune(final UInt64 slotLimit) {}

  @Override
  public void subscribeRequiredBlockRootDropped(
      final RequiredBlockRootDroppedSubscriber subscriber) {}

  @Override
  public void subscribeRequiredBlockRoot(final RequiredBlockRootSubscriber subscriber) {}

  @Override
  public List<T> getItemsDependingOn(
      final Bytes32 blockRoot, final boolean includeIndirectDependents) {
    return Collections.emptyList();
  }

  @Override
  public Set<Bytes32> getAllRequiredBlockRoots() {
    return Collections.emptySet();
  }

  @Override
  public Optional<T> get(final Bytes32 itemRoot) {
    return Optional.empty();
  }

  @Override
  public boolean contains(final Bytes32 itemRoot) {
    return false;
  }

  @Override
  public boolean contains(final T item) {
    return false;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public void remove(final T item) {}

  @Override
  public void add(final T item) {}
}
