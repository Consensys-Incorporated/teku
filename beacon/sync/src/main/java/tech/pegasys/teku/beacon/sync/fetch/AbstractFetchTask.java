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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * @param <K> - the type of the key used to fetch
 * @param <T> - the type of the object to fetch
 */
public abstract class AbstractFetchTask<K, T> {

  private static final Comparator<Eth2Peer> SHUFFLING_COMPARATOR =
      Comparator.comparingDouble(p -> Math.random());

  private final AtomicInteger numberOfRuns = new AtomicInteger(0);
  private final Set<NodeId> queriedPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  private final P2PNetwork<Eth2Peer> eth2Network;
  private final Optional<Eth2Peer> preferredPeer;

  protected AbstractFetchTask(
      final P2PNetwork<Eth2Peer> eth2Network, final Optional<Eth2Peer> preferredPeer) {
    this.eth2Network = eth2Network;
    this.preferredPeer = preferredPeer;
  }

  public int getNumberOfRetries() {
    return Math.max(0, numberOfRuns.get() - 1);
  }

  public void cancel() {
    cancelled.set(true);
  }

  protected void trackQueriedPeer(final Eth2Peer peer) {
    queriedPeers.add(peer.getId());
  }

  protected boolean isCancelled() {
    return cancelled.get();
  }

  /**
   * Uses a preferred {@link Eth2Peer} or selects a random one from the network and gets a result
   * using the {@link #fetch(Eth2Peer)} implementation. It also tracks the number of runs and the
   * already queried peers.
   */
  public SafeFuture<FetchResult<T>> run() {
    if (isCancelled()) {
      return SafeFuture.completedFuture(FetchResult.createFailed(Status.CANCELLED));
    }

    final Optional<Eth2Peer> maybePeer = findPeer();

    if (maybePeer.isEmpty()) {
      return SafeFuture.completedFuture(FetchResult.createFailed(Status.NO_AVAILABLE_PEERS));
    }
    final Eth2Peer peer = maybePeer.get();

    numberOfRuns.incrementAndGet();
    trackQueriedPeer(peer);

    return fetch(peer);
  }

  public abstract K getKey();

  abstract SafeFuture<FetchResult<T>> fetch(final Eth2Peer peer);

  private Optional<Eth2Peer> findPeer() {
    return preferredPeer.filter(this::peerIsNotQueried).or(this::findRandomPeer);
  }

  private Optional<Eth2Peer> findRandomPeer() {
    return eth2Network
        .streamPeers()
        .filter(this::peerIsNotQueried)
        .min(
            Comparator.comparingInt(Eth2Peer::getOutstandingRequests)
                .thenComparing(SHUFFLING_COMPARATOR));
  }

  private boolean peerIsNotQueried(final Eth2Peer peer) {
    return !queriedPeers.contains(peer.getId());
  }
}
