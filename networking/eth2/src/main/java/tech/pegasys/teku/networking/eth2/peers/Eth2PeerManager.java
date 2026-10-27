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

package tech.pegasys.teku.networking.eth2.peers;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.RootCauseExceptionHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2PeerManager implements PeerLookup, PeerHandler {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration STATUS_RECEIVED_TIMEOUT = Duration.ofSeconds(10);

  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final Eth2PeerFactory eth2PeerFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;

  private final Subscribers<PeerConnectedSubscriber<Eth2Peer>> connectSubscribers =
      Subscribers.create(true);
  private final ConcurrentHashMap<NodeId, Eth2Peer> connectedPeerMap = new ConcurrentHashMap<>();

  private final BeaconChainMethods rpcMethods;

  private final Duration eth2RpcPingInterval;
  private final int eth2RpcOutstandingPingThreshold;

  private final Duration eth2StatusUpdateInterval;

  Eth2PeerManager(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final CombinedChainDataClient combinedChainDataClient,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final Eth2PeerFactory eth2PeerFactory,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final RpcEncoding rpcEncoding,
      final Duration eth2RpcPingInterval,
      final int eth2RpcOutstandingPingThreshold,
      final Duration eth2StatusUpdateInterval,
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
      final DasReqRespLogger dasLogger,
      final Optional<LightClientUpdateStore> lightClientUpdateStore) {
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.eth2PeerFactory = eth2PeerFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.rpcMethods =
        BeaconChainMethods.create(
            spec,
            asyncRunner,
            this,
            combinedChainDataClient,
            custodyGroupCountManagerSupplier,
            recentChainData,
            metricsSystem,
            statusMessageFactory,
            metadataMessagesFactory,
            rpcEncoding,
            dataColumnSidecarArchiveReconstructor,
            dasLogger,
            lightClientUpdateStore);
    this.eth2RpcPingInterval = eth2RpcPingInterval;
    this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
    this.eth2StatusUpdateInterval = eth2StatusUpdateInterval;
  }

  public static Eth2PeerManager create(
      final AsyncRunner asyncRunner,
      final CombinedChainDataClient combinedChainDataClient,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final MetadataMessagesFactory metadataMessagesFactory,
      final MetricsSystem metricsSystem,
      final SubnetSubscriptionService attestationSubnetService,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final RpcEncoding rpcEncoding,
      final StatusMessageFactory statusMessageFactory,
      final Optional<Checkpoint> requiredCheckpoint,
      final Duration eth2RpcPingInterval,
      final int eth2RpcOutstandingPingThreshold,
      final Duration eth2StatusUpdateInterval,
      final TimeProvider timeProvider,
      final int peerBlocksRateLimit,
      final int peerBlobSidecarsRateLimit,
      final int peerRequestLimit,
      final Spec spec,
      final DiscoveryNodeIdExtractor discoveryNodeIdExtractor,
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
      final DasReqRespLogger dasLogger,
      final Optional<LightClientUpdateStore> lightClientUpdateStore) {
    attestationSubnetService.subscribeToUpdates(
        metadataMessagesFactory::updateAttestationSubnetIds);
    syncCommitteeSubnetService.subscribeToUpdates(
        metadataMessagesFactory::updateSyncCommitteeSubnetIds);

    return new Eth2PeerManager(
        spec,
        asyncRunner,
        combinedChainDataClient,
        custodyGroupCountManagerSupplier,
        combinedChainDataClient.getRecentChainData(),
        metricsSystem,
        new Eth2PeerFactory(
            spec,
            metricsSystem,
            combinedChainDataClient,
            blobKzgCommitmentsProvider,
            statusMessageFactory,
            metadataMessagesFactory,
            timeProvider,
            requiredCheckpoint,
            peerBlocksRateLimit,
            peerBlobSidecarsRateLimit,
            peerRequestLimit,
            discoveryNodeIdExtractor),
        statusMessageFactory,
        metadataMessagesFactory,
        rpcEncoding,
        eth2RpcPingInterval,
        eth2RpcOutstandingPingThreshold,
        eth2StatusUpdateInterval,
        dataColumnSidecarArchiveReconstructor,
        dasLogger,
        lightClientUpdateStore);
  }

  public MetadataMessage getMetadataMessage() {
    final MetadataMessageSchema<?> schema =
        recentChainData.getCurrentSpec().getSchemaDefinitions().getMetadataMessageSchema();
    return metadataMessagesFactory.createMetadataMessage(schema);
  }

  private void setUpPeriodicTasksForPeer(final Eth2Peer peer) {
    Cancellable periodicStatusUpdateTask = periodicallyUpdatePeerStatus(peer);
    Cancellable periodicPingTask = periodicallyPingPeer(peer);
    peer.subscribeDisconnect(
        (reason, locallyInitiated) -> {
          periodicStatusUpdateTask.cancel();
          periodicPingTask.cancel();
        });
  }

  Cancellable periodicallyUpdatePeerStatus(final Eth2Peer peer) {
    return asyncRunner.runWithFixedDelay(
        () ->
            peer.sendStatus()
                .finish(
                    () -> LOG.trace("Updated status for peer {}", peer),
                    err -> LOG.debug("Exception updating status for peer {}", peer, err)),
        eth2StatusUpdateInterval,
        err -> LOG.debug("Exception calling runnable for updating peer status.", err));
  }

  Cancellable periodicallyPingPeer(final Eth2Peer peer) {
    return asyncRunner.runWithFixedDelay(
        () -> sendPeriodicPing(peer),
        eth2RpcPingInterval,
        err -> LOG.debug("Exception calling runnable for pinging peer", err));
  }

  @Override
  public void onConnect(final Peer peer) {
    Eth2Peer eth2Peer = eth2PeerFactory.create(peer, rpcMethods);
    final boolean wasAdded = connectedPeerMap.putIfAbsent(peer.getId(), eth2Peer) == null;
    if (!wasAdded) {
      LOG.debug("Duplicate peer connection detected for peer {}. Ignoring peer.", peer.getId());
      return;
    }

    peer.setDisconnectRequestHandler(reason -> eth2Peer.sendGoodbye(reason.getReasonCode()));
    if (peer.connectionInitiatedLocally()) {
      eth2Peer
          .sendStatus()
          .finish(
              () -> LOG.trace("Sent status to {}", peer.getId()),
              RootCauseExceptionHandler.builder()
                  .addCatch(
                      RpcException.class,
                      err -> {
                        LOG.trace("Status message rejected by {}: {}", peer.getId(), err);
                        eth2Peer.disconnectImmediately(
                            Optional.of(DisconnectReason.REMOTE_FAULT), true);
                      })
                  .defaultCatch(
                      err -> {
                        LOG.debug("Failed to send status to {}: {}", peer.getId(), err);
                        eth2Peer.disconnectImmediately(Optional.empty(), true);
                      }));
    } else {
      ensureStatusReceived(eth2Peer);
    }

    eth2Peer.subscribeInitialStatus(
        (status) -> {
          connectSubscribers.forEach(c -> c.onConnected(eth2Peer));
          setUpPeriodicTasksForPeer(eth2Peer);
        });
  }

  private void ensureStatusReceived(final Eth2Peer peer) {
    asyncRunner
        .runAfterDelay(
            () -> {
              if (!peer.hasStatus()) {
                LOG.trace(
                    "Disconnecting peer {} because initial status was not received", peer.getId());
                peer.disconnectCleanly(DisconnectReason.NO_STATUS_RECEIVED).finishStackTrace();
              }
            },
            STATUS_RECEIVED_TIMEOUT)
        .finish(
            () -> {},
            error -> {
              LOG.error(
                  "Error while waiting for peer {} to exchange status. Disconnecting",
                  peer.getId());
              peer.disconnectImmediately(Optional.of(DisconnectReason.NO_STATUS_RECEIVED), true);
            });
  }

  @VisibleForTesting
  void sendPeriodicPing(final Eth2Peer peer) {
    if (peer.getUnansweredPingCount() >= eth2RpcOutstandingPingThreshold) {
      LOG.debug("Disconnecting the peer {} due to PING timeout.", peer.getId());
      peer.disconnectCleanly(DisconnectReason.UNRESPONSIVE).finishStackTrace();
    } else {
      peer.sendPing()
          .finish(
              i -> LOG.trace("Periodic ping returned {} from {}", i, peer.getId()),
              t -> LOG.debug("Ping request failed for peer {}", peer.getId(), t));
    }
  }

  public long subscribeConnect(final PeerConnectedSubscriber<Eth2Peer> subscriber) {
    return connectSubscribers.subscribe(subscriber);
  }

  public void unsubscribeConnect(final long subscriptionId) {
    connectSubscribers.unsubscribe(subscriptionId);
  }

  @Override
  public void onDisconnect(@NotNull final Peer peer) {
    connectedPeerMap.compute(
        peer.getId(),
        (id, existingPeer) -> {
          if (peer.idMatches(existingPeer)) {
            return null;
          }
          return existingPeer;
        });
  }

  public BeaconChainMethods getBeaconChainMethods() {
    return rpcMethods;
  }

  /**
   * Look up peer by id, returning peer result regardless of validation status of the peer.
   *
   * @param nodeId The nodeId of the peer to lookup
   * @return the peer corresponding to this node id.
   */
  @Override
  public Optional<Eth2Peer> getConnectedPeer(final NodeId nodeId) {
    return Optional.ofNullable(connectedPeerMap.get(nodeId));
  }

  public Optional<Eth2Peer> getPeer(final NodeId peerId) {
    return Optional.ofNullable(connectedPeerMap.get(peerId)).filter(this::peerIsReady);
  }

  public Stream<Eth2Peer> streamPeers() {
    return connectedPeerMap.values().stream().filter(this::peerIsReady);
  }

  private boolean peerIsReady(final Eth2Peer peer) {
    return peer.hasStatus();
  }

  public SafeFuture<?> sendGoodbyeToPeers() {
    return SafeFuture.allOf(
        connectedPeerMap.values().stream()
            .map(peer -> peer.disconnectCleanly(DisconnectReason.SHUTTING_DOWN))
            .toArray(SafeFuture[]::new));
  }
}
