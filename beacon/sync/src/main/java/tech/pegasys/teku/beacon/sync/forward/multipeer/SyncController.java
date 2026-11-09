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
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.SyncProgress;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncController {
  // Rate limiting is usually in 1 minute intervals, so it should be reset after such delay
  protected static final Duration SYNC_AWAKE_INTERVAL = Duration.ofSeconds(60);
  private static final Logger LOG = LogManager.getLogger();

  private final Subscribers<SyncSubscriber> subscribers = Subscribers.create(true);

  private final EventThread eventThread;
  private final Executor subscriberExecutor;
  private final RecentChainData recentChainData;
  private final SyncTargetSelector syncTargetSelector;
  private final Sync sync;

  /**
   * The current sync. When empty, no sync has started, otherwise contains the details of the last
   * started sync, which may have completed.
   *
   * <p>Note that this field may be accessed from off the event thread so that the sync status can
   * be queried from any thread. It should only be written from the event thread.
   */
  private Optional<InProgressSync> currentSync = Optional.empty();

  public SyncController(
      final EventThread eventThread,
      final AsyncRunner asyncRunner,
      final Executor subscriberExecutor,
      final RecentChainData recentChainData,
      final SyncTargetSelector syncTargetSelector,
      final Sync sync) {
    this.eventThread = eventThread;
    this.subscriberExecutor = subscriberExecutor;
    this.recentChainData = recentChainData;
    this.syncTargetSelector = syncTargetSelector;
    this.sync = sync;
    // Try to restart sync, could recover stalled sync
    asyncRunner.runWithFixedDelay(
        () -> eventThread.execute(this::onTargetChainsUpdated),
        SYNC_AWAKE_INTERVAL,
        ex -> LOG.error("Unexpected error when trying to awake peer sync", ex));
  }

  /**
   * Notify that chains have been updated.
   *
   * <p>Must be called on the sync event thread.
   */
  public void onTargetChainsUpdated() {
    eventThread.checkOnEventThread();
    final boolean currentlySyncing = isSyncActive();
    final Optional<InProgressSync> newSync = selectNewSyncTarget();
    if (newSync.isEmpty() && currentlySyncing) {
      return;
    }
    currentSync = newSync;
    if (!currentlySyncing && isSyncActive()) {
      notifySubscribers(true);
    }
  }

  private Optional<InProgressSync> selectNewSyncTarget() {
    return syncTargetSelector
        .selectSyncTarget(
            currentSync.filter(InProgressSync::isActiveOrFailed).map(InProgressSync::getSyncTarget))
        .map(this::startSync);
  }

  private void onSyncComplete(final SyncResult result) {
    eventThread.checkOnEventThread();

    if (isSyncActive() || result == SyncResult.TARGET_CHANGED) {
      // A different sync is now running so ignore this change.
      LOG.debug(
          "Ignoring sync complete because another sync is already active. Result: {}", result);
      return;
    }
    LOG.debug(
        "Completed sync to chain {} with result {}",
        currentSync.map(Objects::toString).orElse("<unknown>"),
        result);
    // See if there's a new sync we should start (possibly switching to non-finalized sync)
    boolean isPreviousSyncSpeculative = isSyncSpeculative();
    currentSync = selectNewSyncTarget();
    if (!isSyncActive() && !isPreviousSyncSpeculative) {
      currentSync = Optional.empty();
      notifySubscribers(false);
    }
  }

  public boolean isSyncActive() {
    return currentSync.map(InProgressSync::isActivePrimarySync).orElse(false);
  }

  private boolean isSyncSpeculative() {
    return currentSync.map(InProgressSync::isSpeculative).orElse(false);
  }

  public SyncingStatus getSyncStatus() {
    return currentSync.map(InProgressSync::asSyncingStatus).orElseGet(this::notSyncingStatus);
  }

  public SafeFuture<Optional<SyncProgress>> getSyncProgress() {
    return sync.getSyncProgress();
  }

  private SyncingStatus notSyncingStatus() {
    return new SyncingStatus(false, recentChainData.getHeadSlot());
  }

  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public void unsubscribeFromSyncChanges(final long subscriberId) {
    subscribers.unsubscribe(subscriberId);
  }

  private void notifySubscribers(final boolean syncing) {
    subscriberExecutor.execute(() -> subscribers.deliver(SyncSubscriber::onSyncingChange, syncing));
  }

  private InProgressSync startSync(final SyncTarget syncTarget) {
    eventThread.checkOnEventThread();
    final TargetChain chain = syncTarget.getTargetChain();
    if (currentSync
        .map(current -> !current.isFailed() && current.hasSameTarget(chain))
        .orElse(false)) {
      LOG.trace("Not starting new sync because it has the same target as current sync");
      return currentSync.get();
    }
    LOG.debug(
        "Starting new sync to {} with {} peers finalized: {}, speculative: {}",
        chain.getChainHead(),
        chain.getPeerCount(),
        syncTarget.isFinalizedSync(),
        syncTarget.isSpeculativeSync());
    final UInt64 startSlot = recentChainData.getHeadSlot();
    final SafeFuture<SyncResult> syncResult = sync.syncToChain(chain);
    syncResult.finishAsync(
        this::onSyncComplete,
        error -> {
          LOG.error(
              "Sync process failed to complete: {}", ExceptionUtil.getMessageOrSimpleName(error));
          LOG.debug("Error encountered during sync", error);
          onSyncComplete(SyncResult.FAILED);
        },
        eventThread);
    return new InProgressSync(startSlot, syncTarget, syncResult);
  }

  private class InProgressSync {
    private final UInt64 startSlot;
    private final SyncTarget syncTarget;
    private final SafeFuture<SyncResult> result;

    private InProgressSync(
        final UInt64 startSlot, final SyncTarget syncTarget, final SafeFuture<SyncResult> result) {
      this.startSlot = startSlot;
      this.syncTarget = syncTarget;
      this.result = result;
    }

    public SyncTarget getSyncTarget() {
      return syncTarget;
    }

    public boolean isActive() {
      return !result.isDone();
    }

    public boolean isActiveOrFailed() {
      return isActive() || isFailed();
    }

    public boolean isFailed() {
      return result.isCompletedExceptionally() || result.getNow(null) == SyncResult.FAILED;
    }

    public boolean isActivePrimarySync() {
      return isActive() && !isSpeculative();
    }

    public boolean isSpeculative() {
      return syncTarget.isSpeculativeSync();
    }

    public TargetChain getTargetChain() {
      return syncTarget.getTargetChain();
    }

    public SyncingStatus asSyncingStatus() {
      return result.isDone() || isSpeculative()
          ? notSyncingStatus()
          : new SyncingStatus(
              true,
              recentChainData.getHeadSlot(),
              startSlot,
              getTargetChain().getChainHead().getSlot());
    }

    public boolean hasSameTarget(final TargetChain chain) {
      return getTargetChain().equals(chain);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("startSlot", startSlot)
          .add("syncTarget", syncTarget)
          .add("result", result)
          .toString();
    }
  }
}
