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

package tech.pegasys.teku.dataproviders.generators;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;

public class StateRegenerationBaseSelector {

  private final Spec spec;
  private final Optional<SlotAndBlockRoot> latestEpochBoundary;
  private final Supplier<Optional<BlockRootAndState>> closestAvailableStateSupplier;
  private final StateAndBlockSummaryProvider stateAndBlockProvider;
  private final Optional<StateAndBlockSummary> rebasedStartingPoint;

  /**
   * Loading states from disk is expensive in terms of time and the amount of memory they consume so
   * if we a reasonably close option, prefer replaying blocks over loading the state from disk. This
   * defines what "reasonably close" means in epochs.
   */
  private final long replayToleranceToAvoidLoadingInEpochs;

  public StateRegenerationBaseSelector(
      final Spec spec,
      final Optional<SlotAndBlockRoot> latestEpochBoundary,
      final Supplier<Optional<BlockRootAndState>> closestAvailableStateSupplier,
      final StateAndBlockSummaryProvider stateAndBlockProvider,
      final Optional<StateAndBlockSummary> rebasedStartingPoint,
      final long replayToleranceToAvoidLoadingInEpochs) {
    this.spec = spec;
    this.latestEpochBoundary = latestEpochBoundary;
    this.closestAvailableStateSupplier = closestAvailableStateSupplier;
    this.stateAndBlockProvider = stateAndBlockProvider;
    this.rebasedStartingPoint = rebasedStartingPoint;
    this.replayToleranceToAvoidLoadingInEpochs = replayToleranceToAvoidLoadingInEpochs;
  }

  public StateRegenerationBaseSelector withRebasedStartingPoint(
      final StateAndBlockSummary blockAndState) {
    if (isBetterThanCurrentRebasedStartingPoint(blockAndState)
        && isEqualToOrBetterThanLatestEpochBoundary(blockAndState)) {
      return new StateRegenerationBaseSelector(
          spec,
          latestEpochBoundary,
          closestAvailableStateSupplier,
          stateAndBlockProvider,
          Optional.of(blockAndState),
          replayToleranceToAvoidLoadingInEpochs);
    }
    return this;
  }

  private boolean isEqualToOrBetterThanLatestEpochBoundary(
      final StateAndBlockSummary blockAndState) {
    return latestEpochBoundary.isEmpty()
        || blockAndState.getSlot().isGreaterThanOrEqualTo(latestEpochBoundary.get().getSlot());
  }

  private boolean isBetterThanCurrentRebasedStartingPoint(
      final StateAndBlockSummary blockAndState) {
    return isBetterThan(
        blockAndState.getSlot(), rebasedStartingPoint.map(StateAndBlockSummary::getSlot));
  }

  public SafeFuture<Optional<StateAndBlockSummary>> getBestBase() {
    final Optional<BlockRootAndState> closestAvailableFromStore =
        closestAvailableStateSupplier.get();
    if (closestAvailableFromStore.isEmpty()) {
      // Can't be a valid target state or has since been finalized. No point regenerating.
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<UInt64> storeSlot = closestAvailableFromStore.map(BlockRootAndState::getSlot);
    final Optional<UInt64> epochBoundarySlot =
        getLatestEpochBoundarySlotMinusTolerance(latestEpochBoundary);
    final Optional<UInt64> rebasedSlot = rebasedStartingPoint.map(StateAndBlockSummary::getSlot);

    if (epochBoundarySlot.isPresent()
        && isBestOption(epochBoundarySlot.get(), storeSlot, rebasedSlot)) {
      return stateAndBlockProvider
          .getStateAndBlock(latestEpochBoundary.get().getBlockRoot())
          .thenApply(
              maybeBlockAndState -> {
                if (maybeBlockAndState.isEmpty()) {
                  return getBestBaseExcludingLatestEpochBoundary(closestAvailableFromStore.get());
                } else {
                  return maybeBlockAndState;
                }
              });
    }

    return SafeFuture.completedFuture(
        getBestBaseExcludingLatestEpochBoundary(closestAvailableFromStore.get()));
  }

  private Optional<StateAndBlockSummary> getBestBaseExcludingLatestEpochBoundary(
      final BlockRootAndState closestAvailableFromStore) {
    if (rebasedStartingPoint.isPresent()
        && rebasedStartingPoint
            .get()
            .getSlot()
            .isGreaterThan(closestAvailableFromStore.getSlot())) {
      return rebasedStartingPoint;
    } else {
      return Optional.of(StateAndBlockSummary.create(closestAvailableFromStore.getState()));
    }
  }

  private Optional<UInt64> getLatestEpochBoundarySlotMinusTolerance(
      final Optional<SlotAndBlockRoot> latestEpochBoundary) {
    return latestEpochBoundary
        .map(SlotAndBlockRoot::getSlot)
        .map(
            slot ->
                slot.minusMinZero(
                    replayToleranceToAvoidLoadingInEpochs * spec.getSlotsPerEpoch(slot)));
  }

  @SafeVarargs
  private boolean isBestOption(final UInt64 slot, final Optional<UInt64>... others) {
    return Stream.of(others).allMatch(other -> isBetterThan(slot, other));
  }

  private boolean isBetterThan(final UInt64 slot, final Optional<UInt64> other) {
    return other.isEmpty() || slot.isGreaterThan(other.get());
  }
}
