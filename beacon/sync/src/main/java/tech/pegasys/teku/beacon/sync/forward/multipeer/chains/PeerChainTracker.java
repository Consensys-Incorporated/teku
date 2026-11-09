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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

/**
 * Tracks the {@link TargetChain} available from the current peer set, both for finalized and
 * non-finalized chains.
 */
public class PeerChainTracker {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final EventThread eventThread;
  private final P2PNetwork<Eth2Peer> p2pNetwork;
  private final Subscribers<Runnable> chainsUpdatedSubscribers = Subscribers.create(true);
  private final SyncSourceFactory syncSourceFactory;
  private final TargetChains finalizedChains;
  private final TargetChains nonfinalizedChains;
  private volatile long connectSubscription;

  public PeerChainTracker(
      final Spec spec,
      final EventThread eventThread,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final SyncSourceFactory syncSourceFactory,
      final TargetChains finalizedChains,
      final TargetChains nonfinalizedChains) {
    this.spec = spec;
    this.eventThread = eventThread;
    this.p2pNetwork = p2pNetwork;
    this.syncSourceFactory = syncSourceFactory;
    this.finalizedChains = finalizedChains;
    this.nonfinalizedChains = nonfinalizedChains;
  }

  public void start() {
    connectSubscription =
        p2pNetwork.subscribeConnect(
            peer -> {
              peer.subscribeStatusUpdates(
                  status -> eventThread.execute(() -> onPeerStatusUpdate(peer, status)));
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) ->
                      eventThread.execute(() -> onPeerDisconnected(peer)));
            });
  }

  public void stop() {
    p2pNetwork.unsubscribeConnect(connectSubscription);
  }

  public void subscribeToTargetChainUpdates(final Runnable subscriber) {
    chainsUpdatedSubscribers.subscribe(subscriber);
  }

  private void onPeerDisconnected(final Eth2Peer peer) {
    eventThread.checkOnEventThread();
    final SyncSource syncSource = syncSourceFactory.getOrCreateSyncSource(peer, spec);
    finalizedChains.onPeerDisconnected(syncSource);
    nonfinalizedChains.onPeerDisconnected(syncSource);
    syncSourceFactory.onPeerDisconnected(peer);
  }

  private void onPeerStatusUpdate(final Eth2Peer peer, final PeerStatus status) {
    eventThread.checkOnEventThread();
    if (!peer.isConnected()) {
      LOG.debug("Ignoring update from disconnected peer");
      return;
    }
    final SyncSource syncSource = syncSourceFactory.getOrCreateSyncSource(peer, spec);
    final SlotAndBlockRoot finalizedChainHead =
        new SlotAndBlockRoot(
            status.getFinalizedCheckpoint().getEpochStartSlot(spec), status.getFinalizedRoot());
    finalizedChains.onPeerStatusUpdated(syncSource, finalizedChainHead);

    final SlotAndBlockRoot nonFinalizedChainHead =
        new SlotAndBlockRoot(status.getHeadSlot(), status.getHeadRoot());
    nonfinalizedChains.onPeerStatusUpdated(syncSource, nonFinalizedChainHead);

    chainsUpdatedSubscribers.forEach(Runnable::run);
  }
}
