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

package tech.pegasys.teku.networking.p2p.connection;

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.networking.p2p.reputation.DefaultReputationManager.COOLDOWN_PERIOD;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;

public class ConnectionManager extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 STABLE_CONNECTION_THRESHOLD_MILLIS = UInt64.valueOf(2000);
  protected static final Duration WARMUP_DISCOVERY_INTERVAL = Duration.ofSeconds(1);
  protected static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);
  protected static final Duration REQUESTED_DISCOVERY_INTERVAL = Duration.ofSeconds(5);
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final P2PNetwork<? extends Peer> network;
  private final Set<PeerAddress> staticPeers;
  private final DiscoveryService discoveryService;
  private final PeerSelectionStrategy peerSelectionStrategy;
  private final Counter attemptedConnectionCounter;
  private final Counter successfulConnectionCounter;
  private final Counter failedConnectionCounter;
  private final PeerPools peerPools;
  private final Collection<Predicate<DiscoveryPeer>> peerPredicates = new CopyOnWriteArrayList<>();

  private volatile long peerConnectedSubscriptionId;
  private volatile Cancellable periodicPeerSearch;
  private final AtomicBoolean peerSearchInProgress = new AtomicBoolean(false);
  private UInt64 nextRequestedPeerSearchTime = UInt64.ZERO;

  private final Map<PeerAddress, Integer> lastStaticPeerReconnectionDelay =
      new ConcurrentHashMap<>();

  public ConnectionManager(
      final MetricsSystem metricsSystem,
      final DiscoveryService discoveryService,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final P2PNetwork<? extends Peer> network,
      final PeerSelectionStrategy peerSelectionStrategy,
      final List<PeerAddress> peerAddresses,
      final PeerPools peerPools) {
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.staticPeers = new HashSet<>(peerAddresses);
    this.discoveryService = discoveryService;
    this.peerSelectionStrategy = peerSelectionStrategy;
    this.timeProvider = timeProvider;

    final LabelledMetric<Counter> connectionAttemptCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "peer_connection_attempt_count_total",
            "Total number of outbound connection attempts made",
            "status");
    attemptedConnectionCounter = connectionAttemptCounter.labels("attempted");
    successfulConnectionCounter = connectionAttemptCounter.labels("successful");
    failedConnectionCounter = connectionAttemptCounter.labels("failed");
    this.peerPools = peerPools;
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.trace("Starting discovery manager");
    synchronized (this) {
      staticPeers.forEach(this::createPersistentConnection);
    }
    createRecurrentSearchTask();
    peerConnectedSubscriptionId = network.subscribeConnect(this::onPeerConnected);
    return SafeFuture.COMPLETE;
  }

  private void createRecurrentSearchTask() {
    searchForPeers().alwaysRun(this::createNextSearchPeerTask).finish(this::logSearchError);
  }

  private void logSearchError(final Throwable throwable) {
    LOG.error("Error while searching for peers", throwable);
  }

  private void createNextSearchPeerTask() {
    if (network.getPeerCount() == 0) {
      // Retry fast until we have at least one connection with peers
      LOG.trace("Retrying peer search, no connected peers yet");
      cancelPeerSearchTask();
      this.periodicPeerSearch =
          asyncRunner.runCancellableAfterDelay(
              this::createRecurrentSearchTask, WARMUP_DISCOVERY_INTERVAL, this::logSearchError);
    } else {
      // Long term task, run when we have peers connected
      LOG.trace("Establishing peer search task with long delay");
      cancelPeerSearchTask();
      this.periodicPeerSearch =
          asyncRunner.runWithFixedDelay(
              () -> searchForPeers().finish(this::logSearchError),
              DISCOVERY_INTERVAL,
              this::logSearchError);
    }
  }

  public void requestPeerSearch() {
    if (!tryStartRequestedPeerSearch()) {
      return;
    }
    asyncRunner
        .runAsync(() -> executePeerSearch().finish(this::logSearchError))
        .finish(
            error -> {
              peerSearchInProgress.set(false);
              logSearchError(error);
            });
  }

  private synchronized boolean tryStartRequestedPeerSearch() {
    if (!isRunning()) {
      LOG.trace("Not running so not searching for peers");
      return false;
    }
    final UInt64 now = timeProvider.getTimeInMillis();
    if (now.isLessThan(nextRequestedPeerSearchTime)) {
      LOG.trace("Peer search request ignored because a recent request was already processed");
      return false;
    }
    if (!peerSearchInProgress.compareAndSet(false, true)) {
      LOG.trace("Peer search already in progress");
      return false;
    }
    nextRequestedPeerSearchTime = now.plus(REQUESTED_DISCOVERY_INTERVAL.toMillis());
    return true;
  }

  private void connectToBestPeers(final Collection<DiscoveryPeer> additionalPeersToConsider) {
    peerSelectionStrategy
        .selectPeersToConnect(
            network,
            peerPools,
            () ->
                Stream.concat(
                        additionalPeersToConsider.stream(), discoveryService.streamKnownPeers())
                    .filter(this::isPeerValid)
                    .collect(Collectors.toSet()))
        .forEach(this::attemptConnection);
  }

  private SafeFuture<Void> searchForPeers() {
    if (!tryStartPeerSearch()) {
      return SafeFuture.COMPLETE;
    }
    return executePeerSearch();
  }

  private boolean tryStartPeerSearch() {
    if (!isRunning()) {
      LOG.trace("Not running so not searching for peers");
      return false;
    }
    if (!peerSearchInProgress.compareAndSet(false, true)) {
      LOG.trace("Peer search already in progress");
      return false;
    }
    return true;
  }

  private SafeFuture<Void> executePeerSearch() {
    LOG.trace("Searching for peers");
    return discoveryService
        .searchForPeers()
        .orTimeout(30, TimeUnit.SECONDS)
        .<Void>handle(
            (peers, error) -> {
              if (error == null) {
                connectToBestPeers(peers);
              } else {
                LOG.debug("Discovery failed", error);
                connectToBestPeers(emptyList());
              }
              return null;
            })
        .alwaysRun(() -> peerSearchInProgress.set(false));
  }

  private void attemptConnection(final PeerAddress peerAddress) {
    LOG.trace("Attempting to connect to {}", peerAddress.getId());
    attemptedConnectionCounter.inc();
    network
        .connect(peerAddress)
        .finish(
            peer -> {
              LOG.trace("Successfully connected to peer {}", peer.getId());
              successfulConnectionCounter.inc();
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) -> peerPools.forgetPeer(peer.getId()));
            },
            error -> {
              LOG.trace(() -> "Failed to connect to peer: " + peerAddress.getId(), error);
              failedConnectionCounter.inc();
              peerPools.forgetPeer(peerAddress.getId());
            });
  }

  private void onPeerConnected(final Peer peer) {
    peerSelectionStrategy
        .selectPeersToDisconnect(network, peerPools)
        .forEach(
            peerToDrop ->
                peerToDrop.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS).finishTrace(LOG));
  }

  @Override
  protected SafeFuture<?> doStop() {
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    cancelPeerSearchTask();
    return SafeFuture.COMPLETE;
  }

  private void cancelPeerSearchTask() {
    final Cancellable peerSearchTask = this.periodicPeerSearch;
    if (peerSearchTask != null) {
      peerSearchTask.cancel();
    }
  }

  public synchronized void addStaticPeer(final PeerAddress peerAddress) {
    if (!staticPeers.contains(peerAddress)) {
      staticPeers.add(peerAddress);
      createPersistentConnection(peerAddress);
    }
  }

  private void createPersistentConnection(final PeerAddress peerAddress) {
    maintainPersistentConnection(peerAddress).finishTrace(LOG);
  }

  private SafeFuture<Peer> maintainPersistentConnection(final PeerAddress peerAddress) {
    if (!isRunning()) {
      // We've been stopped so halt the process.
      return new SafeFuture<>();
    }
    LOG.debug("Connecting to peer {}", peerAddress);
    peerPools.addPeerToPool(peerAddress.getId(), PeerConnectionType.STATIC);
    attemptedConnectionCounter.inc();
    return network
        .connect(peerAddress)
        .thenApply(
            peer -> {
              LOG.debug("Connection to peer {} was successful", peer.getId());
              successfulConnectionCounter.inc();
              final UInt64 connectionTimeMillis = timeProvider.getTimeInMillis();
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) -> {
                    if (wasConnectionStable(connectionTimeMillis)) {
                      // A client may disconnect us immediately after the connection is established
                      // The delay must be reset only after a stable connection is lost
                      lastStaticPeerReconnectionDelay.remove(peerAddress);
                    }

                    final Duration retryDelay = computeStaticPeerRetryDelay(peerAddress);
                    LOG.debug(
                        "Peer {} disconnected. Will try to reconnect in {} sec",
                        peerAddress,
                        retryDelay.toSeconds());
                    asyncRunner
                        .runAfterDelay(() -> maintainPersistentConnection(peerAddress), retryDelay)
                        .finishDebug(LOG);
                  });
              return peer;
            })
        .exceptionallyCompose(
            error -> {
              final Duration retryDelay = computeStaticPeerRetryDelay(peerAddress);
              LOG.debug(
                  "Connection to {} failed: {}. Will retry in {} sec",
                  peerAddress,
                  error,
                  retryDelay.toSeconds());
              failedConnectionCounter.inc();
              return asyncRunner.runAfterDelay(
                  () -> maintainPersistentConnection(peerAddress), retryDelay);
            });
  }

  /** A connection is considered stable if it lasted more than the defined threshold */
  private boolean wasConnectionStable(final UInt64 connectionTimeMillis) {
    final UInt64 immediateDisconnectionTime =
        connectionTimeMillis.plus(STABLE_CONNECTION_THRESHOLD_MILLIS);
    return timeProvider.getTimeInMillis().isGreaterThanOrEqualTo(immediateDisconnectionTime);
  }

  // compute increasing delay, up to 120s, between attempts to connect to a static peer
  // each failure increases the delay by a factor of 3: 3s, 9s, 27s, 81s, 120s, 120s, ...
  @VisibleForTesting
  Duration computeStaticPeerRetryDelay(final PeerAddress peerAddress) {
    return Duration.ofSeconds(
        lastStaticPeerReconnectionDelay.compute(
            peerAddress,
            (__, lastAttempt) -> {
              if (lastAttempt == null) {
                return 3;
              } else {
                return computeIncrease(lastAttempt);
              }
            }));
  }

  @VisibleForTesting
  Integer getLastStaticPeerReconnectionDelay(final PeerAddress peerAddress) {
    return lastStaticPeerReconnectionDelay.get(peerAddress);
  }

  // separate method to make errorprone happy
  private static int computeIncrease(final int lastAttempt) {
    return Math.min(lastAttempt * 3, COOLDOWN_PERIOD.intValue());
  }

  public void addPeerPredicate(final Predicate<DiscoveryPeer> predicate) {
    peerPredicates.add(predicate);
  }

  private boolean isPeerValid(final DiscoveryPeer peer) {
    return !peer.getNodeAddress().getAddress().isAnyLocalAddress()
        && peerPredicates.stream().allMatch(predicate -> predicate.test(peer));
  }
}
