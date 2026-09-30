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

package tech.pegasys.teku.networking.p2p.discovery;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

public class DiscoveryNetwork<P extends Peer> extends DelegatingP2PNetwork<P> {
  private static final Logger LOG = LogManager.getLogger();

  public static final String ATTESTATION_SUBNET_ENR_FIELD = "attnets";
  public static final String SYNC_COMMITTEE_SUBNET_ENR_FIELD = "syncnets";
  public static final String DAS_CUSTODY_GROUP_COUNT_ENR_FIELD = "cgc";
  public static final String ETH2_ENR_FIELD = "eth2";
  public static final String NEXT_FORK_DIGEST_ENR_FIELD = "nfd";

  private final Spec spec;
  private final P2PNetwork<P> p2pNetwork;
  private final DiscoveryService discoveryService;
  private final ConnectionManager connectionManager;
  private final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;

  private volatile Optional<EnrForkId> enrForkId = Optional.empty();

  protected DiscoveryNetwork(
      final P2PNetwork<P> p2pNetwork,
      final DiscoveryService discoveryService,
      final ConnectionManager connectionManager,
      final Spec spec,
      final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    super(p2pNetwork);
    this.p2pNetwork = p2pNetwork;
    this.discoveryService = discoveryService;
    this.connectionManager = connectionManager;
    this.spec = spec;
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    initialize();
  }

  public void initialize() {
    setPreGenesisForkInfo();
    getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5PreGenesis);

    // Set connection manager peer predicate so that we don't attempt to connect peers with
    // different fork digests
    connectionManager.addPeerPredicate(this::dontConnectPeersWithDifferentForkDigests);
  }

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.allOfFailFast(p2pNetwork.start(), discoveryService.start())
        .thenCompose(__ -> connectionManager.start())
        .thenRun(() -> getEnr().ifPresent(StatusLogger.STATUS_LOG::listeningForDiscv5));
  }

  @Override
  public SafeFuture<?> stop() {
    return connectionManager
        .stop()
        .handleComposed(
            (__, err) -> {
              if (err != null) {
                LOG.warn("Error shutting down connection manager", err);
              }
              return SafeFuture.allOf(p2pNetwork.stop(), discoveryService.stop());
            });
  }

  public void addStaticPeer(final String peerAddress) {
    connectionManager.addStaticPeer(p2pNetwork.createPeerAddress(peerAddress));
  }

  public void requestPeerSearch() {
    connectionManager.requestPeerSearch();
  }

  @Override
  public Optional<String> getEnr() {
    return discoveryService.getEnr();
  }

  @Override
  public Optional<UInt256> getDiscoveryNodeId() {
    return discoveryService
        .getNodeId()
        .map(bytes -> UInt256.valueOf(new BigInteger(1, bytes.toArray())));
  }

  @Override
  public Optional<List<String>> getDiscoveryAddresses() {
    return discoveryService.getDiscoveryAddresses();
  }

  public void setLongTermAttestationSubnetSubscriptions(final Iterable<Integer> subnetIds) {
    discoveryService.updateCustomENRField(
        ATTESTATION_SUBNET_ENR_FIELD,
        currentSchemaDefinitionsSupplier
            .getAttnetsENRFieldSchema()
            .ofBits(subnetIds)
            .sszSerialize());
  }

  public void setSyncCommitteeSubnetSubscriptions(final Iterable<Integer> subnetIds) {
    discoveryService.updateCustomENRField(
        SYNC_COMMITTEE_SUBNET_ENR_FIELD,
        currentSchemaDefinitionsSupplier
            .getSyncnetsENRFieldSchema()
            .ofBits(subnetIds)
            .sszSerialize());
  }

  public void setDASTotalCustodyGroupCount(final int count) {
    if (count < 0) {
      throw new IllegalArgumentException(
          String.format("Custody group count should be a positive number, but was %s", count));
    }
    LOG.debug("Setting cgc in ENR to: {}", count);
    discoveryService.updateCustomENRField(
        DAS_CUSTODY_GROUP_COUNT_ENR_FIELD, Bytes.ofUnsignedInt(count).trimLeadingZeros());
  }

  public void setNextForkDigest(final Bytes4 nextForkDigest) {
    LOG.debug("Setting nfd in ENR to: {}", nextForkDigest.toUnprefixedHexString());
    discoveryService.updateCustomENRField(
        NEXT_FORK_DIGEST_ENR_FIELD, SszBytes4.of(nextForkDigest).sszSerialize());
  }

  public void setPreGenesisForkInfo() {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final Bytes4 genesisForkVersion = genesisSpec.getConfig().getGenesisForkVersion();
    final EnrForkId enrForkId =
        new EnrForkId(
            genesisSpec.miscHelpers().computeForkDigest(genesisForkVersion, Bytes32.ZERO),
            genesisForkVersion,
            SpecConfig.FAR_FUTURE_EPOCH);
    setEth2(enrForkId);
    this.enrForkId = Optional.of(enrForkId);
  }

  public void setForkInfo(
      final ForkInfo currentForkInfo,
      final Bytes4 currentForkDigest,
      final Optional<Fork> nextForkInfo,
      final Optional<BlobParameters> nextBpoFork,
      final Optional<Bytes4> nextForkDigest) {
    // If no future fork is planned (either BPO or a hard fork), set next_fork_epoch =
    // FAR_FUTURE_EPOCH to signal this
    final UInt64 nextForkEpoch =
        nextBpoFork
            .map(
                bpoFork ->
                    nextForkInfo
                        .filter(forkInfo -> forkInfo.getEpoch().isLessThan(bpoFork.epoch()))
                        .map(Fork::getEpoch)
                        .orElse(bpoFork.epoch()))
            .or(() -> nextForkInfo.map(Fork::getEpoch))
            .orElse(SpecConfig.FAR_FUTURE_EPOCH);
    // next_fork_version only changes for hard forks, not BPO forks, so it is the current version
    // unless the hard fork is at next_fork_epoch. If no future fork is planned, set
    // next_fork_version = current_fork_version to signal this
    final Bytes4 nextVersion =
        nextForkInfo
            .filter(forkInfo -> forkInfo.getEpoch().equals(nextForkEpoch))
            .map(Fork::getCurrentVersion)
            .orElse(currentForkInfo.getFork().getCurrentVersion());

    final EnrForkId enrForkId = new EnrForkId(currentForkDigest, nextVersion, nextForkEpoch);
    setEth2(enrForkId);

    this.enrForkId = Optional.of(enrForkId);

    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      final Bytes4 nfdEnrField = nextForkDigest.orElse(Bytes4.ZERO);
      setNextForkDigest(nfdEnrField);
    }
  }

  private void setEth2(final EnrForkId enrForkId) {
    LOG.debug("Setting eth2 in ENR to: {}", enrForkId);
    discoveryService.updateCustomENRField(ETH2_ENR_FIELD, enrForkId.sszSerialize());
  }

  private boolean dontConnectPeersWithDifferentForkDigests(final DiscoveryPeer peer) {
    return enrForkId
        .map(EnrForkId::getForkDigest)
        .flatMap(
            localForkDigest ->
                peer.getEnrForkId()
                    .map(EnrForkId::getForkDigest)
                    .map(peerForkDigest -> peerForkDigest.equals(localForkDigest)))
        .orElse(false);
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<P> subscriber) {
    return p2pNetwork.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    p2pNetwork.unsubscribeConnect(subscriptionId);
  }

  @Override
  public Optional<P> getPeer(final NodeId id) {
    return p2pNetwork.getPeer(id);
  }

  @Override
  public Stream<P> streamPeers() {
    return p2pNetwork.streamPeers();
  }

  @Override
  public Optional<DiscoveryNetwork<?>> getDiscoveryNetwork() {
    return Optional.of(this);
  }

  public DiscoveryService getDiscoveryService() {
    return discoveryService;
  }
}
