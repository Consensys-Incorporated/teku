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

package tech.pegasys.teku.storage.server.state;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.cache.CacheLoader;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.dataproviders.generators.StreamingStateRegenerator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.Database;

class StateCacheLoader extends CacheLoader<UInt64, BeaconState> {
  private static final Logger LOG = LogManager.getLogger();
  private final int stateRebuildTimeoutSeconds;
  private final Database database;
  private final long maxRegenerateSlots;
  private final FinalizedStateCache finalizedStateCache;
  private final Spec spec;

  StateCacheLoader(
      final Spec spec,
      final Database database,
      final int stateRebuildTimeoutSeconds,
      final long maxRegenerateSlots,
      final FinalizedStateCache finalizedStateCache) {
    this.database = database;
    this.stateRebuildTimeoutSeconds = stateRebuildTimeoutSeconds;
    this.maxRegenerateSlots = maxRegenerateSlots;
    this.finalizedStateCache = finalizedStateCache;
    this.spec = spec;
  }

  @Override
  public BeaconState load(final UInt64 key) {
    return regenerateState(key).orElseThrow(FinalizedStateCache.StateUnavailableException::new);
  }

  private Optional<BeaconState> regenerateState(final UInt64 slot) {
    final Optional<BeaconState> maybeState = database.getLatestAvailableFinalizedState(slot);
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final BeaconState state = maybeState.get();
    try {
      return Optional.of(
          regenerateStateWithinReasonableTime(slot, state)
              .get(stateRebuildTimeoutSeconds, TimeUnit.SECONDS));
    } catch (ExecutionException | InterruptedException e) {
      LOG.warn("Failed to regenerate state for slot {}", slot, e);
      return Optional.empty();
    } catch (TimeoutException e) {
      LOG.error(
          "Timed out trying to regenerate state at slot {} starting from slot {} within {} seconds",
          slot,
          state.getSlot(),
          stateRebuildTimeoutSeconds);
      return Optional.empty();
    }
  }

  private SafeFuture<BeaconState> regenerateStateWithinReasonableTime(
      final UInt64 slot, final BeaconState stateFromDisk) {
    final Optional<BeaconState> latestStateFromCache =
        finalizedStateCache.getLatestStateFromCache(slot);
    final BeaconState preState =
        latestStateFromCache
            .filter(
                stateFromCache -> stateFromCache.getSlot().compareTo(stateFromDisk.getSlot()) >= 0)
            .orElse(stateFromDisk);
    if (preState.getSlot().equals(slot)) {
      return SafeFuture.completedFuture(preState);
    }
    final long regenerateSlotCount = slot.minusMinZero(stateFromDisk.getSlot()).longValue();
    LOG.trace("Slots to regenerate state from: {}", regenerateSlotCount);
    if (regenerateSlotCount > maxRegenerateSlots) {
      LOG.error(
          "Refusing to regenerate a state that is {} slots from what we have stored",
          regenerateSlotCount);
      return SafeFuture.failedFuture(new FinalizedStateCache.StateUnavailableException());
    }
    try (final Stream<SignedBeaconBlock> blocks =
        database.streamFinalizedBlocks(preState.getSlot().plus(ONE), slot)) {
      final BeaconState state = StreamingStateRegenerator.regenerate(spec, preState, blocks);
      finalizedStateCache.getAvailableSlots().add(state.getSlot());
      return SafeFuture.completedFuture(state);
    }
  }
}
