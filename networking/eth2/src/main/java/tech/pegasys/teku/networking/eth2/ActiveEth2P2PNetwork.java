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

package tech.pegasys.teku.networking.eth2;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionProofGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.config.Eth2Context;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ActiveEth2P2PNetwork extends DelegatingP2PNetwork<Eth2Peer> implements Eth2P2PNetwork {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final DiscoveryNetwork<?> discoveryNetwork;
  private final Eth2PeerManager peerManager;
  private final EventChannels eventChannels;
  private final RecentChainData recentChainData;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final GossipEncoding gossipEncoding;
  private final GossipConfigurator gossipConfigurator;
  private final SubnetSubscriptionService attestationSubnetService;
  private final SubnetSubscriptionService syncCommitteeSubnetService;
  private final SubnetSubscriptionService dataColumnSidecarSubnetService;
  private final SubnetSubscriptionService executionProofSubnetService;
  private final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  private final AtomicBoolean gossipStarted = new AtomicBoolean(false);

  private final GossipForkManager gossipForkManager;

  private long discoveryNetworkAttestationSubnetsSubscription;
  private long discoveryNetworkSyncCommitteeSubnetsSubscription;

  private volatile Cancellable gossipUpdateTask;
  private UInt64 currentForkEpoch;
  private Bytes4 currentForkDigest;
  private final boolean allTopicsFilterEnabled;

  public ActiveEth2P2PNetwork(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final DiscoveryNetwork<?> discoveryNetwork,
      final Eth2PeerManager peerManager,
      final GossipForkManager gossipForkManager,
      final EventChannels eventChannels,
      final RecentChainData recentChainData,
      final SubnetSubscriptionService attestationSubnetService,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final SubnetSubscriptionService dataColumnSidecarSubnetService,
      final SubnetSubscriptionService executionProofSubnetService,
      final GossipEncoding gossipEncoding,
      final GossipConfigurator gossipConfigurator,
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider,
      final boolean allTopicsFilterEnabled) {
    super(discoveryNetwork);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.discoveryNetwork = discoveryNetwork;
    this.peerManager = peerManager;
    this.gossipForkManager = gossipForkManager;
    this.eventChannels = eventChannels;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.gossipConfigurator = gossipConfigurator;
    this.attestationSubnetService = attestationSubnetService;
    this.syncCommitteeSubnetService = syncCommitteeSubnetService;
    this.dataColumnSidecarSubnetService = dataColumnSidecarSubnetService;
    this.executionProofSubnetService = executionProofSubnetService;
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
    this.allTopicsFilterEnabled = allTopicsFilterEnabled;
  }

  @Override
  public SafeFuture<?> start() {
    if (recentChainData.isPreGenesis() || recentChainData.isPreForkChoice()) {
      throw new IllegalStateException(
          getClass().getSimpleName()
              + " should only be started after "
              + recentChainData.getClass().getSimpleName()
              + " is fully initialized.");
    }
    // Set the current fork info prior to discovery starting up.
    final ForkInfo currentForkInfo = recentChainData.getCurrentForkInfo().orElseThrow();
    final Bytes4 currentForkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    updateForkInfo(currentForkInfo, currentForkDigest);
    return super.start().thenAccept(r -> startup());
  }

  private synchronized void startup() {
    state.set(State.RUNNING);
    processedAttestationSubscriptionProvider.subscribe(gossipForkManager::publishAttestation);
    eventChannels.subscribe(BlockGossipChannel.class, gossipForkManager::publishBlock);
    eventChannels.subscribe(BlobSidecarGossipChannel.class, gossipForkManager::publishBlobSidecar);
    eventChannels.subscribe(
        DataColumnSidecarGossipChannel.class,
        (sidecar, __) -> gossipForkManager.publishDataColumnSidecar(sidecar));
    eventChannels.subscribe(
        ExecutionProofGossipChannel.class, gossipForkManager::publishExecutionProof);
    eventChannels.subscribe(
        ExecutionPayloadGossipChannel.class, gossipForkManager::publishExecutionPayload);

    if (recentChainData.isCloseToInSync()) {
      startGossip();
    }
    peerManager.subscribeConnect(peer -> onPeerConnected());
  }

  private void onPeerConnected() {
    if (gossipStarted.get() || state.get() != State.RUNNING) {
      return;
    }
    if (recentChainData.isCloseToInSync()) {
      startGossip();
    }
  }

  private synchronized void startGossip() {
    if (!gossipStarted.compareAndSet(false, true)) {
      return;
    }

    LOG.info("Starting eth2 gossip");

    discoveryNetworkAttestationSubnetsSubscription =
        attestationSubnetService.subscribeToUpdates(
            discoveryNetwork::setLongTermAttestationSubnetSubscriptions);
    discoveryNetworkSyncCommitteeSubnetsSubscription =
        syncCommitteeSubnetService.subscribeToUpdates(
            discoveryNetwork::setSyncCommitteeSubnetSubscriptions);
    final UInt64 currentEpoch = recentChainData.getCurrentEpoch().orElseThrow();
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      recentChainData
          .getNextForkDigest(currentEpoch)
          .ifPresent(discoveryNetwork::setNextForkDigest);
    }
    gossipForkManager.configureGossipForEpoch(currentEpoch);
    if (allTopicsFilterEnabled) {
      setAllTopicScoring();
    }
    setTopicScoringParams();
  }

  private synchronized void stopGossip() {
    if (gossipStarted.compareAndSet(true, false)) {
      LOG.warn("Stopping eth2 gossip while node is syncing");
      gossipUpdateTask.cancel();
      gossipForkManager.stopGossip();
      attestationSubnetService.unsubscribe(discoveryNetworkAttestationSubnetsSubscription);
      syncCommitteeSubnetService.unsubscribe(discoveryNetworkSyncCommitteeSubnetsSubscription);
    }
  }

  @Override
  public void onSyncStateChanged(final boolean isCloseToInSync, final boolean isOptimistic) {
    gossipForkManager.onOptimisticHeadChanged(isOptimistic);

    if (state.get() != State.RUNNING) {
      return;
    }
    if (isCloseToInSync) {
      startGossip();
    } else {
      stopGossip();
    }
  }

  private void setTopicScoringParams() {
    gossipUpdateTask =
        asyncRunner.runWithFixedDelay(
            this::updateDynamicTopicScoring,
            allTopicsFilterEnabled ? Duration.ofMinutes(1) : Duration.ZERO,
            Duration.ofMinutes(1),
            (err) ->
                LOG.error(
                    "Encountered error while attempting to updating gossip topic scoring", err));
  }

  private void setAllTopicScoring() {
    LOG.debug("Update all topic scoring on digest {}", currentForkDigest);
    getEth2Context()
        .thenApply(gossipConfigurator::configureAllTopics)
        .thenAccept(discoveryNetwork::updateGossipTopicScoring)
        .finishStackTrace();
  }

  private SafeFuture<?> updateDynamicTopicScoring() {
    LOG.trace("Update dynamic topic scoring");
    return getEth2Context()
        .thenApply(gossipConfigurator::configureDynamicTopics)
        .thenAccept(discoveryNetwork::updateGossipTopicScoring);
  }

  private SafeFuture<Eth2Context> getEth2Context() {
    final ChainHead chainHead = recentChainData.getChainHead().orElseThrow();
    final Bytes4 forkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);

    return chainHead
        .getState()
        .thenApply(
            chainHeadState -> {
              final UInt64 activeValidatorsEpoch =
                  spec.getMaxLookaheadEpoch(chainHeadState).min(currentEpoch);
              final int activeValidators =
                  spec.countActiveValidators(chainHeadState, activeValidatorsEpoch);

              return Eth2Context.builder()
                  .currentSlot(currentSlot)
                  .activeValidatorCount(activeValidators)
                  .forkDigest(forkDigest)
                  .gossipEncoding(gossipEncoding)
                  .build();
            });
  }

  @Override
  public synchronized SafeFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return SafeFuture.COMPLETE;
    }

    stopGossip();

    return peerManager
        .sendGoodbyeToPeers()
        .exceptionally(
            error -> {
              LOG.debug("Failed to send goodbye to peers on shutdown", error);
              return null;
            })
        .thenCompose(__ -> super.stop());
  }

  @Override
  public Optional<Eth2Peer> getPeer(final NodeId id) {
    return peerManager.getPeer(id);
  }

  @Override
  public Stream<Eth2Peer> streamPeers() {
    return peerManager.streamPeers();
  }

  @Override
  public int getPeerCount() {
    return Math.toIntExact(streamPeers().count());
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<Eth2Peer> subscriber) {
    return peerManager.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    peerManager.unsubscribeConnect(subscriptionId);
  }

  public BeaconChainMethods getBeaconChainMethods() {
    return peerManager.getBeaconChainMethods();
  }

  @Override
  public void onEpoch(final UInt64 epoch) {
    if (gossipStarted.get()) {
      gossipForkManager.configureGossipForEpoch(epoch);
    }
    recentChainData
        .getForkInfo(epoch)
        .ifPresent(
            forkInfo -> {
              final Bytes4 forkDigest = recentChainData.getForkDigest(epoch);
              updateForkInfo(forkInfo, forkDigest);
            });
  }

  @Override
  public synchronized void subscribeToAttestationSubnetId(final int subnetId) {
    gossipForkManager.subscribeToAttestationSubnetId(subnetId);
  }

  @Override
  public synchronized void unsubscribeFromAttestationSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromAttestationSubnetId(subnetId);
  }

  @Override
  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIndices) {
    attestationSubnetService.setSubscriptions(subnetIndices);
  }

  @Override
  public void subscribeToSyncCommitteeSubnetId(final int subnetId) {
    gossipForkManager.subscribeToSyncCommitteeSubnetId(subnetId);
    syncCommitteeSubnetService.addSubscription(subnetId);
  }

  @Override
  public void unsubscribeFromSyncCommitteeSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromSyncCommitteeSubnetId(subnetId);
    syncCommitteeSubnetService.removeSubscription(subnetId);
  }

  @Override
  public void subscribeToDataColumnSidecarSubnetId(final int subnetId) {
    gossipForkManager.subscribeToDataColumnSidecarSubnetId(subnetId);
    dataColumnSidecarSubnetService.addSubscription(subnetId);
  }

  @Override
  public void unsubscribeFromDataColumnSidecarSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromDataColumnSidecarSubnetId(subnetId);
    dataColumnSidecarSubnetService.removeSubscription(subnetId);
  }

  @Override
  public void subscribeToExecutionProofSubnetId(final int subnetId) {
    gossipForkManager.subscribeToExecutionProofSubnetId(subnetId);
    executionProofSubnetService.addSubscription(subnetId);
  }

  @Override
  public void unsubscribeFromExecutionProofSubnetId(final int subnetId) {
    gossipForkManager.unsubscribeFromExecutionProofSubnetId(subnetId);
    executionProofSubnetService.removeSubscription(subnetId);
  }

  @Override
  public MetadataMessage getMetadata() {
    return peerManager.getMetadataMessage();
  }

  @Override
  public void publishSyncCommitteeMessage(final ValidatableSyncCommitteeMessage message) {
    gossipForkManager.publishSyncCommitteeMessage(message);
  }

  @Override
  public void publishSyncCommitteeContribution(
      final SignedContributionAndProof signedContributionAndProof) {
    gossipForkManager.publishSyncCommitteeContribution(signedContributionAndProof);
  }

  @Override
  public void publishProposerSlashing(final ProposerSlashing proposerSlashing) {
    gossipForkManager.publishProposerSlashing(proposerSlashing);
  }

  @Override
  public void publishAttesterSlashing(final AttesterSlashing attesterSlashing) {
    gossipForkManager.publishAttesterSlashing(attesterSlashing);
  }

  @Override
  public void publishVoluntaryExit(final SignedVoluntaryExit signedVoluntaryExit) {
    gossipForkManager.publishVoluntaryExit(signedVoluntaryExit);
  }

  @Override
  public void publishSignedBlsToExecutionChange(
      final SignedBlsToExecutionChange signedBlsToExecutionChange) {
    gossipForkManager.publishSignedBlsToExecutionChanges(signedBlsToExecutionChange);
  }

  @Override
  public void publishPayloadAttestationMessage(
      final PayloadAttestationMessage payloadAttestationMessage) {
    gossipForkManager.publishPayloadAttestationMessage(payloadAttestationMessage);
  }

  @Override
  public void publishExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    gossipForkManager.publishExecutionPayloadBid(signedExecutionPayloadBid);
  }

  @Override
  public void publishProposerPreferences(
      final SignedProposerPreferences signedProposerPreferences) {
    gossipForkManager.publishProposerPreferences(signedProposerPreferences);
  }

  @Override
  public void publishLightClientFinalityUpdate(final LightClientFinalityUpdate finalityUpdate) {
    gossipForkManager.publishLightClientFinalityUpdate(finalityUpdate);
  }

  @Override
  public void publishLightClientOptimisticUpdate(
      final LightClientOptimisticUpdate optimisticUpdate) {
    gossipForkManager.publishLightClientOptimisticUpdate(optimisticUpdate);
  }

  @VisibleForTesting
  Eth2PeerManager getPeerManager() {
    return peerManager;
  }

  private synchronized void updateForkInfo(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    final Optional<BlobParameters> maybeBpoFork =
        recentChainData.getBpoForkByForkDigest(forkDigest);
    final UInt64 forkEpoch =
        maybeBpoFork.map(BlobParameters::epoch).orElse(forkInfo.getFork().getEpoch());
    if (currentForkEpoch != null
        && currentForkDigest != null
        // Will NOT update fork info when it's the same fork digest as the current one or the epoch
        // of the hard fork (or BPO fork) is prior to the current one
        && (currentForkDigest.equals(forkDigest) || forkEpoch.isLessThan(currentForkEpoch))) {
      return;
    }

    currentForkEpoch = forkEpoch;
    currentForkDigest = forkDigest;

    final Optional<Fork> nextFork = recentChainData.getNextFork(forkInfo.getFork());

    final Optional<BlobParameters> nextBpoFork;
    if (maybeBpoFork.isPresent()) {
      nextBpoFork = spec.getNextBpoFork(maybeBpoFork.get().epoch());
    } else {
      nextBpoFork = spec.getNextBpoFork(forkInfo.getFork().getEpoch());
    }

    final Optional<Bytes4> nextForkDigest;
    if (maybeBpoFork.isPresent()) {
      nextForkDigest = recentChainData.getNextForkDigest(maybeBpoFork.get().epoch());
    } else {
      nextForkDigest = recentChainData.getNextForkDigest(forkInfo.getFork().getEpoch());
    }

    discoveryNetwork.setForkInfo(forkInfo, forkDigest, nextFork, nextBpoFork, nextForkDigest);
  }

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.of(discoveryNetwork);
  }
}
