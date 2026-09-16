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

package tech.pegasys.teku.beacon.sync.events;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncStateTracker extends Service
    implements SyncStateProvider, OptimisticHeadSubscriber, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final ForwardSync syncService;
  private final Eth2P2PNetwork network;
  private final RecentChainData recentChainData;
  private final Subscribers<SyncStateSubscriber> subscribers = Subscribers.create(true);
  private final EventLogger eventLogger;
  private final SettableGauge isSyncingGauge;

  private final Duration startupTimeout;
  private final int startupTargetPeerCount;

  private boolean startingUp;
  private boolean syncActive = false;
  private long peerConnectedSubscriptionId;
  private long syncSubscriptionId;
  private boolean headIsOptimistic = false;

  /** True once we've told the user we're behind the known chain head, so we only say it once. */
  private boolean reportedBehindKnownChainHead = false;

  /**
   * True once we've announced a sync starting and haven't yet announced reaching sync. Forward sync
   * can start and stop many times before it gets there - retrying, or losing every peer - and none
   * of those is a new sync as far as the user is concerned.
   */
  private boolean reportedSyncStart = false;

  private volatile SyncState currentState;

  public SyncStateTracker(
      final AsyncRunner asyncRunner,
      final ForwardSync syncService,
      final Eth2P2PNetwork network,
      final RecentChainData recentChainData,
      final int startupTargetPeerCount,
      final Duration startupTimeout,
      final MetricsSystem metricsSystem) {
    this(
        asyncRunner,
        syncService,
        network,
        recentChainData,
        startupTargetPeerCount,
        startupTimeout,
        EVENT_LOG,
        metricsSystem);
  }

  SyncStateTracker(
      final AsyncRunner asyncRunner,
      final ForwardSync syncService,
      final Eth2P2PNetwork network,
      final RecentChainData recentChainData,
      final int startupTargetPeerCount,
      final Duration startupTimeout,
      final EventLogger eventLogger,
      final MetricsSystem metricsSystem) {
    this.asyncRunner = asyncRunner;
    this.syncService = syncService;
    this.network = network;
    this.recentChainData = recentChainData;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeout = startupTimeout;
    this.eventLogger = eventLogger;
    this.isSyncingGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "node_syncing_active",
            "Indicator to show the node sync is currently running.");
    if (startupTargetPeerCount == 0 || startupTimeout.toMillis() == 0) {
      startingUp = false;
      currentState = SyncState.IN_SYNC;
    } else {
      startingUp = true;
      currentState = SyncState.START_UP;
    }
    this.isSyncingGauge.set(currentState.isSyncing() ? 1.0 : 0.0);
  }

  @Override
  public SyncState getCurrentSyncState() {
    return currentState;
  }

  @Override
  public void onOptimisticHeadChanged(final boolean active) {
    logSyncStateOnOptimisticHeadChanged(headIsOptimistic, active);
    headIsOptimistic = active;
    updateCurrentState();
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  @Override
  public long subscribeToSyncStateChangesAndUpdate(final SyncStateSubscriber subscriber) {
    final long subscriptionId = subscribeToSyncStateChanges(subscriber);
    subscriber.onSyncStateChange(getCurrentSyncState());
    return subscriptionId;
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  /**
   * Forward sync only notifies us when it starts and stops, so re-evaluate every slot to notice our
   * head reaching the rest of the network while no sync is active.
   */
  @Override
  public synchronized void onSlot(final UInt64 slot) {
    updateCurrentState();
  }

  private void updateCurrentState() {
    final SyncState previousState = currentState;
    boolean behindKnownChainHead = false;
    if (headIsOptimistic) {
      currentState = syncActive ? SyncState.OPTIMISTIC_SYNCING : SyncState.AWAITING_EL;
    } else if (syncActive) {
      currentState = SyncState.SYNCING;
    } else if (startingUp) {
      currentState = SyncState.START_UP;
    } else if (isBehindKnownChainHead()) {
      // Forward sync isn't running - it may have found no suitable peers, or stalled and be waiting
      // to retry - but we have no reason to believe our head is the network's. Reporting IN_SYNC
      // here would let block production and payload attribute calculation try to regenerate a state
      // hundreds of slots ahead of our head, which is prohibitively expensive. Forward sync remains
      // free to start again at any time and will move us back to SYNCING.
      behindKnownChainHead = true;
      currentState = SyncState.SYNCING;
    } else {
      currentState = SyncState.IN_SYNC;
    }

    if (behindKnownChainHead && !reportedBehindKnownChainHead) {
      reportedBehindKnownChainHead = true;
      knownChainHeadSlot()
          .ifPresentOrElse(
              knownHead ->
                  eventLogger.syncStoppedWhileBehindHead(
                      knownHead.minusMinZero(recentChainData.getHeadSlot()).longValue()),
              eventLogger::notInSyncWithoutPeers);
    }

    if (currentState != previousState) {
      // the catch up, whatever caused it, is announced exactly once here
      if (currentState.isInSync()) {
        reportedBehindKnownChainHead = false;
        // we owe a completion if we announced a start, or if we were visibly syncing without one -
        // held behind the head, waiting for peers, or waiting for the EL
        if (reportedSyncStart || previousState.isSyncing()) {
          reportedSyncStart = false;
          eventLogger.syncCompleted();
        }
      }
      isSyncingGauge.set(currentState.isSyncing() ? 1.0 : 0.0);
      subscribers.deliver(SyncStateSubscriber::onSyncStateChange, currentState);
    }
  }

  /**
   * True when we can't believe our own head is the network's. Deliberately says nothing about the
   * current slot: our head block being old only means we're behind if there are actually blocks out
   * there we're missing. If the chain itself has a long run of empty slots our peers' heads are
   * just as old as ours, so we correctly stay in sync and keep proposing.
   *
   * <p>Uses the same tolerance {@link RecentChainData#isCloseToInSync()} applies to enable gossip,
   * just measured against the network's head rather than the current slot.
   */
  private boolean isBehindKnownChainHead() {
    return knownChainHeadSlot()
        .map(knownHead -> !recentChainData.isHeadCloseToSlot(knownHead))
        // With nobody to compare against we can't tell a stale head from being at the tip. A node
        // configured to expect peers but left with none has to assume the worst, otherwise it would
        // try to build on a head that may be hours old. A node configured for no peers (a single
        // node network) is authoritative on its own and must carry on proposing.
        .orElseGet(this::expectsPeers);
  }

  private boolean expectsPeers() {
    return startupTargetPeerCount > 0;
  }

  /**
   * The head slot we believe the network is at, taken from peer status messages. Once we have
   * enough peers to have a choice we take the second highest head, so that a single peer
   * overstating its head can't convince us we're behind. Empty when we have no peers to ask.
   */
  private Optional<UInt64> knownChainHeadSlot() {
    final List<UInt64> peerHeadSlots =
        network
            .streamPeers()
            .filter(Eth2Peer::hasStatus)
            .map(peer -> peer.getStatus().getHeadSlot())
            .sorted(Comparator.reverseOrder())
            .toList();
    if (peerHeadSlots.isEmpty()) {
      return Optional.empty();
    }
    // sorted highest first: second highest to exclude anomalies when we have the choice, otherwise
    // the highest we have
    final int trustedHeadIndex =
        peerHeadSlots.size() >= Math.max(2, startupTargetPeerCount) ? 1 : 0;
    return Optional.of(peerHeadSlots.get(trustedHeadIndex));
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    LOG.debug(
        "Starting sync state tracker with initial state: {}, target peer count: {}, startup timeout: {}",
        currentState,
        startupTargetPeerCount,
        startupTimeout);
    peerConnectedSubscriptionId = network.subscribeConnect(peer -> onPeerConnected());
    asyncRunner.runAfterDelay(this::onStartupTimeout, startupTimeout).finishStackTrace();
    syncSubscriptionId = syncService.subscribeToSyncChanges(this::onSyncingChanged);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    syncService.unsubscribeFromSyncChanges(syncSubscriptionId);
    return SafeFuture.COMPLETE;
  }

  private synchronized void onSyncingChanged(final boolean active) {
    logSyncStateOnSyncingChanged(syncActive, active);
    syncActive = active;
    updateCurrentState();
  }

  private void logSyncStateOnOptimisticHeadChanged(
      final boolean wasHeadPreviouslyOptimistic, final boolean isHeadOptimistic) {

    if (wasHeadPreviouslyOptimistic == isHeadOptimistic || startingUp) {
      return;
    }

    if (isHeadOptimistic) {
      if (syncActive) {
        eventLogger.headTurnedOptimisticWhileSyncing();
      } else {
        eventLogger.headTurnedOptimisticWhileInSync();
      }
      return;
    }

    if (syncActive) {
      eventLogger.headNoLongerOptimisticWhileSyncing();
    }
    // otherwise we're leaving AWAITING_EL, and updateCurrentState() announces the catch up
  }

  private void logSyncStateOnSyncingChanged(
      final boolean wasPreviouslySyncing, final boolean isSyncing) {

    if (wasPreviouslySyncing == isSyncing) {
      return;
    }

    if (isSyncing) {
      if (!reportedSyncStart) {
        reportedSyncStart = true;
        eventLogger.syncStart();
      }
      return;
    }

    if (headIsOptimistic) {
      eventLogger.syncCompletedWhileHeadIsOptimistic();
    }
    // otherwise updateCurrentState() announces it, if this stop actually reached the head
  }

  private synchronized void markStartupComplete() {
    startingUp = false;
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    updateCurrentState();
  }

  private synchronized void onStartupTimeout() {
    if (!startingUp) {
      return;
    }
    LOG.debug("Startup timeout reached");
    markStartupComplete();
  }

  private synchronized void onPeerConnected() {
    if (network.getPeerCount() >= startupTargetPeerCount) {
      LOG.debug("Target peer count ({}) was reached", startupTargetPeerCount);
      markStartupComplete();
    }
  }
}
