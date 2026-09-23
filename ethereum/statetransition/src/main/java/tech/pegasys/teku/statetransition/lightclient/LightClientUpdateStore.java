/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.storage.api.LightClientUpdateChannel;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;

public class LightClientUpdateStore {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final LightClientUpdateChannel channel;

  private final ConcurrentNavigableMap<UInt64, StoredUpdate<LightClientUpdate>>
      bestUpdatesByPeriod = new ConcurrentSkipListMap<>();
  private final AtomicReference<Optional<StoredUpdate<LightClientFinalityUpdate>>>
      latestFinalityUpdate = new AtomicReference<>(Optional.empty());
  private final AtomicReference<Optional<StoredUpdate<LightClientOptimisticUpdate>>>
      latestOptimisticUpdate = new AtomicReference<>(Optional.empty());

  public LightClientUpdateStore(final Spec spec) {
    this(spec, LightClientUpdateChannel.NOOP);
  }

  public LightClientUpdateStore(final Spec spec, final LightClientUpdateChannel channel) {
    this.spec = spec;
    this.channel = channel;
  }

  public synchronized void addUpdate(
      final LightClientUpdate update,
      final Bytes32 signatureBlockRoot,
      final BiPredicate<UInt64, Bytes32> isCanonical) {
    final UInt64 signatureSlot = update.getSignatureSlot().get();
    final UInt64 attestedPeriod =
        syncCommitteePeriodAtSlot(update.getAttestedHeader().getBeacon().getSlot());
    if (!attestedPeriod.equals(syncCommitteePeriodAtSlot(signatureSlot))) {
      return;
    }

    if (!isCanonical.test(signatureSlot, signatureBlockRoot)) {
      return;
    }

    final StoredUpdate<LightClientUpdate> candidate =
        new StoredUpdate<>(update, signatureSlot, signatureBlockRoot);
    final StoredUpdate<LightClientUpdate> best =
        bestUpdatesByPeriod.merge(
            attestedPeriod,
            candidate,
            (existing, incoming) ->
                isBetterUpdate(incoming.update(), existing.update()) ? incoming : existing);

    if (best.equals(candidate)) {
      channel
          .onNewBestLightClientUpdate(attestedPeriod, update, signatureBlockRoot)
          .finishError(LOG);
    }
  }

  public synchronized void loadUpdates(final Collection<StoredLightClientUpdate> updates) {
    updates.forEach(
        stored ->
            bestUpdatesByPeriod.put(
                stored.period(),
                new StoredUpdate<>(
                    stored.update(),
                    stored.update().getSignatureSlot().get(),
                    stored.signatureBlockRoot())));
  }

  public synchronized void removeNonCanonicalUpdates(
      final UInt64 fromPeriod, final BiPredicate<UInt64, Bytes32> isCanonical) {
    final Predicate<StoredUpdate<?>> canonical =
        stored -> isCanonical.test(stored.signatureSlot(), stored.signatureBlockRoot());

    final List<UInt64> removedPeriods = new ArrayList<>();
    bestUpdatesByPeriod
        .tailMap(fromPeriod)
        .entrySet()
        .removeIf(
            entry -> {
              if (canonical.test(entry.getValue())) {
                return false;
              }
              removedPeriods.add(entry.getKey());
              return true;
            });
    latestFinalityUpdate.updateAndGet(current -> current.filter(canonical));
    latestOptimisticUpdate.updateAndGet(current -> current.filter(canonical));

    if (!removedPeriods.isEmpty()) {
      channel.onRemoveBestLightClientUpdates(removedPeriods).finishError(LOG);
    }
  }

  public synchronized void addFinalityUpdate(
      final LightClientFinalityUpdate finalityUpdate,
      final Bytes32 signatureBlockRoot,
      final BiPredicate<UInt64, Bytes32> isCanonical) {
    final UInt64 signatureSlot = finalityUpdate.getSignatureSlot().get();
    if (!isCanonical.test(signatureSlot, signatureBlockRoot)) {
      return;
    }

    final StoredUpdate<LightClientFinalityUpdate> incoming =
        new StoredUpdate<>(finalityUpdate, signatureSlot, signatureBlockRoot);

    latestFinalityUpdate.updateAndGet(
        current -> {
          if (current.isEmpty()) {
            return Optional.of(incoming);
          }

          return isLaterUpdate(
                  finalityUpdate.getAttestedHeader().getBeacon().getSlot(),
                  incoming.signatureSlot(),
                  current.get().update().getAttestedHeader().getBeacon().getSlot(),
                  current.get().signatureSlot())
              ? Optional.of(incoming)
              : current;
        });
  }

  public synchronized void addOptimisticUpdate(
      final LightClientOptimisticUpdate optimisticUpdate,
      final Bytes32 signatureBlockRoot,
      final BiPredicate<UInt64, Bytes32> isCanonical) {
    final UInt64 signatureSlot = optimisticUpdate.getSignatureSlot().get();
    if (!isCanonical.test(signatureSlot, signatureBlockRoot)) {
      return;
    }

    final StoredUpdate<LightClientOptimisticUpdate> incoming =
        new StoredUpdate<>(optimisticUpdate, signatureSlot, signatureBlockRoot);

    latestOptimisticUpdate.updateAndGet(
        current -> {
          if (current.isEmpty()) {
            return Optional.of(incoming);
          }

          return isLaterUpdate(
                  optimisticUpdate.getAttestedHeader().getBeacon().getSlot(),
                  incoming.signatureSlot(),
                  current.get().update().getAttestedHeader().getBeacon().getSlot(),
                  current.get().signatureSlot())
              ? Optional.of(incoming)
              : current;
        });
  }

  public List<LightClientUpdate> getBestUpdatesInRange(final UInt64 startPeriod, final int count) {
    final ConcurrentNavigableMap<UInt64, StoredUpdate<LightClientUpdate>> updatesInRange =
        bestUpdatesByPeriod.subMap(startPeriod, true, startPeriod.plus(count), false);
    if (updatesInRange.isEmpty()) {
      return List.of();
    }

    final UInt64 earliestPeriod = updatesInRange.firstKey();
    final List<LightClientUpdate> consecutiveUpdates = new ArrayList<>();

    for (final Map.Entry<UInt64, StoredUpdate<LightClientUpdate>> entry :
        updatesInRange.entrySet()) {
      if (!entry.getKey().equals(earliestPeriod.plus(consecutiveUpdates.size()))) {
        break;
      }
      consecutiveUpdates.add(entry.getValue().update());
    }

    return consecutiveUpdates;
  }

  public synchronized void pruneUpdatesBefore(final UInt64 period) {
    bestUpdatesByPeriod.headMap(period).clear();
    channel.onPruneBestLightClientUpdatesBefore(period).finishError(LOG);
  }

  public Optional<LightClientFinalityUpdate> getLatestFinalityUpdate() {
    return latestFinalityUpdate.get().map(StoredUpdate::update);
  }

  public Optional<LightClientOptimisticUpdate> getLatestOptimisticUpdate() {
    return latestOptimisticUpdate.get().map(StoredUpdate::update);
  }

  private record StoredUpdate<T>(T update, UInt64 signatureSlot, Bytes32 signatureBlockRoot) {}

  /** {@code is_better_update}. */
  private boolean isBetterUpdate(
      final LightClientUpdate newUpdate, final LightClientUpdate oldUpdate) {
    final int maxActiveParticipants = newUpdate.getSyncAggregate().getSyncCommitteeBits().size();
    final int newUpdateActiveParticipants =
        newUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();
    final int oldUpdateActiveParticipants =
        oldUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();

    final boolean newUpdateHasSupermajority =
        newUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;
    final boolean oldUpdateHasSupermajority =
        oldUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;

    if (newUpdateHasSupermajority != oldUpdateHasSupermajority) {
      return newUpdateHasSupermajority;
    }

    if (!newUpdateHasSupermajority && newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    final UInt64 newUpdateSignatureSlot = newUpdate.getSignatureSlot().get();
    final UInt64 oldUpdateSignatureSlot = oldUpdate.getSignatureSlot().get();
    final UInt64 newUpdateAttestedSlot = newUpdate.getAttestedHeader().getBeacon().getSlot();
    final UInt64 oldUpdateAttestedSlot = oldUpdate.getAttestedHeader().getBeacon().getSlot();

    final boolean newUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(newUpdate)
            && syncCommitteePeriodAtSlot(newUpdateAttestedSlot)
                .equals(syncCommitteePeriodAtSlot(newUpdateSignatureSlot));
    final boolean oldUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(oldUpdate)
            && syncCommitteePeriodAtSlot(oldUpdateAttestedSlot)
                .equals(syncCommitteePeriodAtSlot(oldUpdateSignatureSlot));

    if (newUpdateHasRelevantSyncCommittee != oldUpdateHasRelevantSyncCommittee) {
      return newUpdateHasRelevantSyncCommittee;
    }

    final boolean newUpdateHasFinality = isFinalityUpdate(newUpdate);
    final boolean oldUpdateHasFinality = isFinalityUpdate(oldUpdate);

    if (newUpdateHasFinality != oldUpdateHasFinality) {
      return newUpdateHasFinality;
    }

    if (newUpdateHasFinality) {
      final boolean newUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(newUpdate.getFinalizedHeader().getBeacon().getSlot())
              .equals(syncCommitteePeriodAtSlot(newUpdateAttestedSlot));
      final boolean oldUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(oldUpdate.getFinalizedHeader().getBeacon().getSlot())
              .equals(syncCommitteePeriodAtSlot(oldUpdateAttestedSlot));

      if (newUpdateHasSyncCommitteeFinality != oldUpdateHasSyncCommitteeFinality) {
        return newUpdateHasSyncCommitteeFinality;
      }
    }

    if (newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    if (!newUpdateAttestedSlot.equals(oldUpdateAttestedSlot)) {
      return newUpdateAttestedSlot.isLessThan(oldUpdateAttestedSlot);
    }

    return newUpdateSignatureSlot.isLessThan(oldUpdateSignatureSlot);
  }

  /** {@code is_sync_committee_update}. */
  private boolean isSyncCommitteeUpdate(final LightClientUpdate update) {
    return !update.getNextSyncCommitteeBranch().isDefault();
  }

  /** {@code is_finality_update}. */
  public static boolean isFinalityUpdate(final LightClientUpdate update) {
    return !update.getFinalityBranch().isDefault();
  }

  private boolean isLaterUpdate(
      final UInt64 newAttestedSlot,
      final UInt64 newSignatureSlot,
      final UInt64 oldAttestedSlot,
      final UInt64 oldSignatureSlot) {
    if (!newAttestedSlot.equals(oldAttestedSlot)) {
      return newAttestedSlot.isGreaterThan(oldAttestedSlot);
    }

    return newSignatureSlot.isGreaterThan(oldSignatureSlot);
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return spec.getSyncCommitteeUtilRequired(slot)
        .computeSyncCommitteePeriod(spec.computeEpochAtSlot(slot));
  }
}
