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

package tech.pegasys.teku.networking.eth2.peers;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static tech.pegasys.teku.networking.p2p.connection.PeerConnectionType.RANDOMLY_SELECTED;
import static tech.pegasys.teku.networking.p2p.connection.PeerConnectionType.SCORE_BASED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class Eth2PeerSelectionStrategy implements PeerSelectionStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final TargetPeerRange targetPeerCountRange;
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory;
  private final ReputationManager reputationManager;
  private final Shuffler shuffler;

  public Eth2PeerSelectionStrategy(
      final TargetPeerRange targetPeerCountRange,
      final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory,
      final ReputationManager reputationManager,
      final Shuffler shuffler) {
    this.targetPeerCountRange = targetPeerCountRange;
    this.peerSubnetSubscriptionsFactory = peerSubnetSubscriptionsFactory;
    this.reputationManager = reputationManager;
    this.shuffler = shuffler;
  }

  @Override
  public List<PeerAddress> selectPeersToConnect(
      final P2PNetwork<?> network,
      final PeerPools peerPools,
      final Supplier<? extends Collection<DiscoveryPeer>> candidates) {
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);
    final int peersRequiredForPeerCount =
        targetPeerCountRange.getPeersToAdd(network.getPeerCount());
    final int randomlySelectedPeerCount = getCurrentRandomlySelectedPeerCount(network, peerPools);
    final int randomlySelectedPeersToAdd =
        targetPeerCountRange.getRandomlySelectedPeersToAdd(randomlySelectedPeerCount);

    final int peersRequiredForSubnets = peerSubnetSubscriptions.getSubscribersRequired();
    final int scoreBasedPeersToAdd =
        Math.max(peersRequiredForPeerCount - randomlySelectedPeersToAdd, peersRequiredForSubnets);
    final int maxPeersToAdd = scoreBasedPeersToAdd + randomlySelectedPeersToAdd;
    LOG.trace("Connecting to up to {} known peers", maxPeersToAdd);
    if (maxPeersToAdd == 0) {
      return emptyList();
    }

    final List<DiscoveryPeer> allCandidatePeers = new ArrayList<>(candidates.get());
    final List<PeerAddress> selectedPeers = new ArrayList<>();

    if (randomlySelectedPeersToAdd > 0) {
      selectedPeers.addAll(
          selectAndRemoveRandomPeers(
              network, peerPools, randomlySelectedPeersToAdd, allCandidatePeers));
    }

    if (scoreBasedPeersToAdd > 0) {
      selectedPeers.addAll(
          selectPeersByScore(
              network, peerSubnetSubscriptions, scoreBasedPeersToAdd, allCandidatePeers));
    }
    return unmodifiableList(selectedPeers); // Unmodifiable to make errorprone happy
  }

  private List<PeerAddress> selectAndRemoveRandomPeers(
      final P2PNetwork<?> network,
      final PeerPools peerPools,
      final int randomlySelectedPeersToAdd,
      final List<DiscoveryPeer> allCandidatePeers) {
    final List<PeerAddress> selectedPeers = new ArrayList<>();
    shuffler.shuffle(allCandidatePeers);
    while (!allCandidatePeers.isEmpty() && selectedPeers.size() < randomlySelectedPeersToAdd) {
      final DiscoveryPeer candidate = allCandidatePeers.removeFirst();
      checkCandidate(candidate, network)
          .ifPresent(
              peerAddress -> {
                peerPools.addPeerToPool(peerAddress.getId(), RANDOMLY_SELECTED);
                selectedPeers.add(peerAddress);
              });
    }
    return selectedPeers;
  }

  private List<PeerAddress> selectPeersByScore(
      final P2PNetwork<?> network,
      final PeerSubnetSubscriptions peerSubnetSubscriptions,
      final int scoreBasedPeersToAdd,
      final List<DiscoveryPeer> allCandidatePeers) {
    final PeerScorer peerScorer = peerSubnetSubscriptions.createScorer();
    return allCandidatePeers.stream()
        .sorted(Comparator.<DiscoveryPeer>comparingInt(peerScorer::scoreCandidatePeer).reversed())
        .flatMap(candidate -> checkCandidate(candidate, network).stream())
        .limit(scoreBasedPeersToAdd)
        .toList();
  }

  private int getCurrentRandomlySelectedPeerCount(
      final P2PNetwork<?> network, final PeerPools peerPools) {
    return (int)
        network
            .streamPeers()
            .filter(peer -> peerPools.getPeerConnectionType(peer.getId()) == RANDOMLY_SELECTED)
            .filter(Peer::connectionInitiatedLocally)
            .count();
  }

  private Optional<PeerAddress> checkCandidate(
      final DiscoveryPeer candidate, final P2PNetwork<?> network) {
    return network
        .createPeerAddress(candidate)
        .filter(reputationManager::isConnectionInitiationAllowed)
        .filter(peerAddress -> !network.isConnected(peerAddress));
  }

  @Override
  public List<Peer> selectPeersToDisconnect(
      final P2PNetwork<?> network, final PeerPools peerPools) {
    final List<Peer> peers = network.streamPeers().map(Peer.class::cast).toList();
    final PeerSubnetSubscriptions peerSubnetSubscriptions =
        peerSubnetSubscriptionsFactory.create(network);
    final int totalOutboundRequirement =
        targetPeerCountRange.getMinimumRandomlySelectedPeerCount()
            + peerSubnetSubscriptions.getSubscribersRequired();
    final int remotelyInitiatedPeersToDrop =
        calculateRemotelyInitiatedPeersToDropCount(peers, totalOutboundRequirement);
    final int peersToDrop =
        Math.max(targetPeerCountRange.getPeersToDrop(peers.size()), remotelyInitiatedPeersToDrop);
    if (peersToDrop == 0) {
      return emptyList();
    }

    final Map<PeerConnectionType, List<Peer>> peersBySource =
        peers.stream()
            .collect(Collectors.groupingBy(peer -> peerPools.getPeerConnectionType(peer.getId())));
    final List<Peer> randomlySelectedPeers =
        peersBySource.getOrDefault(RANDOMLY_SELECTED, emptyList());
    final List<Peer> scoreBasedPeers = peersBySource.getOrDefault(SCORE_BASED, emptyList());
    final List<Peer> remotelyInitiatedRandomlySelectedPeers =
        randomlySelectedPeers.stream().filter(Peer::connectionInitiatedRemotely).toList();

    final PeerScorer peerScorer = peerSubnetSubscriptions.createScorer();
    // Satisfy remote-cap drops from remotely initiated peers first.
    final List<Peer> remotelyInitiatedPeersBeingDropped =
        findRemotelyInitiatedPeersToDrop(
            remotelyInitiatedRandomlySelectedPeers,
            scoreBasedPeers,
            remotelyInitiatedPeersToDrop,
            peerScorer);
    final int additionalPeersToDrop = peersToDrop - remotelyInitiatedPeersBeingDropped.size();
    if (additionalPeersToDrop == 0) {
      LOG.trace(
          "Selected {} remotely initiated peers to disconnect (peerCount={}, "
              + "remotelyInitiatedPeerCount={}, totalOutboundRequirement={})",
          remotelyInitiatedPeersBeingDropped::size,
          peers::size,
          () -> peers.stream().filter(Peer::connectionInitiatedRemotely).count(),
          () -> totalOutboundRequirement);
      return remotelyInitiatedPeersBeingDropped;
    }

    // Fill any remaining drop slots using the normal score-based ordering.
    final List<Peer> peersBeingDropped = new ArrayList<>(remotelyInitiatedPeersBeingDropped);
    final int currentPeerCount = peers.size();
    final List<Peer> locallyInitiatedRandomPeersEligibleForDisconnection =
        selectLocallyInitiatedRandomPeersEligibleForDisconnection(
            randomlySelectedPeers, currentPeerCount);
    // Peers from the randomly selected pool that have been chosen are moved have their special
    // randomly selected status revoked, leaving them in the default pool where they are considered
    // for disconnection based on their score
    locallyInitiatedRandomPeersEligibleForDisconnection.forEach(
        peer -> peerPools.addPeerToPool(peer.getId(), SCORE_BASED));
    Stream.concat(
            locallyInitiatedRandomPeersEligibleForDisconnection.stream(),
            Stream.concat(
                remotelyInitiatedRandomlySelectedPeers.stream(), scoreBasedPeers.stream()))
        .filter(peer -> !peersBeingDropped.contains(peer))
        .sorted(Comparator.comparingInt(peerScorer::scoreExistingPeer))
        .limit(additionalPeersToDrop)
        .forEach(peersBeingDropped::add);
    LOG.trace(
        "Selected {} peers to disconnect (peerCount={}, remotelyInitiatedPeerCount={}, "
            + "remotelyInitiatedPeersDropped={}, locallyInitiatedRandomPeersDemoted={}, "
            + "totalOutboundRequirement={})",
        peersBeingDropped::size,
        peers::size,
        () -> peers.stream().filter(Peer::connectionInitiatedRemotely).count(),
        remotelyInitiatedPeersBeingDropped::size,
        locallyInitiatedRandomPeersEligibleForDisconnection::size,
        () -> totalOutboundRequirement);
    return List.copyOf(peersBeingDropped);
  }

  private int calculateRemotelyInitiatedPeersToDropCount(
      final List<Peer> peers, final int totalOutboundRequirement) {
    final int remotelyInitiatedPeerCount =
        (int) peers.stream().filter(Peer::connectionInitiatedRemotely).count();

    return targetPeerCountRange.getRemotelyInitiatedPeersToDrop(
        remotelyInitiatedPeerCount, totalOutboundRequirement, peers.size());
  }

  private List<Peer> findRemotelyInitiatedPeersToDrop(
      final List<Peer> remotelyInitiatedRandomlySelectedPeers,
      final List<Peer> scoreBasedPeers,
      final int remotelyInitiatedPeersToDropCount,
      final PeerScorer peerScorer) {
    return Stream.concat(
            remotelyInitiatedRandomlySelectedPeers.stream(),
            scoreBasedPeers.stream().filter(Peer::connectionInitiatedRemotely))
        .sorted(Comparator.comparingInt(peerScorer::scoreExistingPeer))
        .limit(remotelyInitiatedPeersToDropCount)
        .toList();
  }

  private List<Peer> selectLocallyInitiatedRandomPeersEligibleForDisconnection(
      final List<Peer> randomlySelectedPeers, final int currentPeerCount) {
    final List<Peer> locallyInitiatedRandomlySelectedPeers =
        randomlySelectedPeers.stream()
            .filter(Peer::connectionInitiatedLocally)
            .collect(Collectors.toCollection(ArrayList::new));
    final int locallyInitiatedRandomlySelectedPeerCount =
        locallyInitiatedRandomlySelectedPeers.size();

    shuffler.shuffle(locallyInitiatedRandomlySelectedPeers);
    final int randomlySelectedPeersToDrop =
        targetPeerCountRange.getRandomlySelectedPeersToDrop(
            locallyInitiatedRandomlySelectedPeerCount, currentPeerCount);

    return locallyInitiatedRandomlySelectedPeers.subList(
        0, Math.min(randomlySelectedPeersToDrop, locallyInitiatedRandomlySelectedPeers.size()));
  }

  @FunctionalInterface
  public interface Shuffler {
    void shuffle(List<?> list);
  }
}
