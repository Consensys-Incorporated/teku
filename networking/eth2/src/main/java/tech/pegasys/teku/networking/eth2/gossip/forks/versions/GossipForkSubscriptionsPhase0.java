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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.GossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsPhase0 implements GossipForkSubscriptions {
  private final List<GossipManager> gossipManagers = new ArrayList<>();
  private final Fork fork;
  protected final Spec spec;
  protected final AsyncRunner asyncRunner;
  protected final MetricsSystem metricsSystem;
  protected final DiscoveryNetwork<?> discoveryNetwork;
  protected final RecentChainData recentChainData;
  protected final GossipEncoding gossipEncoding;

  // Upstream consumers
  private final OperationProcessor<SignedBeaconBlock> blockProcessor;
  private final OperationProcessor<ValidatableAttestation> attestationProcessor;
  private final OperationProcessor<ValidatableAttestation> aggregateProcessor;
  private final OperationProcessor<AttesterSlashing> attesterSlashingProcessor;
  private final OperationProcessor<ProposerSlashing> proposerSlashingProcessor;
  private final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor;

  private AttestationGossipManager attestationGossipManager;
  private AggregateGossipManager aggregateGossipManager;
  private BlockGossipManager blockGossipManager;
  private VoluntaryExitGossipManager voluntaryExitGossipManager;
  private ProposerSlashingGossipManager proposerSlashingGossipManager;
  private AttesterSlashingGossipManager attesterSlashingGossipManager;
  protected DebugDataDumper debugDataDumper;

  public GossipForkSubscriptionsPhase0(
      final Fork fork,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<ValidatableAttestation> attestationProcessor,
      final OperationProcessor<ValidatableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final DebugDataDumper debugDataDumper) {
    this.fork = fork;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.discoveryNetwork = discoveryNetwork;
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
    this.blockProcessor = blockProcessor;
    this.attestationProcessor = attestationProcessor;
    this.aggregateProcessor = aggregateProcessor;
    this.attesterSlashingProcessor = attesterSlashingProcessor;
    this.proposerSlashingProcessor = proposerSlashingProcessor;
    this.voluntaryExitProcessor = voluntaryExitProcessor;
    this.debugDataDumper = debugDataDumper;
  }

  @Override
  public UInt64 getActivationEpoch() {
    return fork.getEpoch();
  }

  @Override
  public final void startGossip(
      final Bytes32 genesisValidatorsRoot, final boolean isOptimisticHead) {
    if (gossipManagers.isEmpty()) {
      final ForkInfo forkInfo = new ForkInfo(fork, genesisValidatorsRoot);
      final Bytes4 forkDigest = recentChainData.getForkDigest(getActivationEpoch());
      addGossipManagers(forkInfo, forkDigest);
    }
    gossipManagers.stream()
        .filter(manager -> manager.isEnabledDuringOptimisticSync() || !isOptimisticHead)
        .forEach(GossipManager::subscribe);
  }

  void addAttestationGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            recentChainData,
            attestationProcessor,
            forkInfo,
            forkDigest,
            debugDataDumper);

    attestationGossipManager =
        new AttestationGossipManager(metricsSystem, attestationSubnetSubscriptions);
    addGossipManager(attestationGossipManager);
  }

  void addBlockGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    blockGossipManager =
        new BlockGossipManager(
            recentChainData,
            spec,
            metricsSystem,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            blockProcessor,
            debugDataDumper);
    addGossipManager(blockGossipManager);
  }

  void addAggregateGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    aggregateGossipManager =
        new AggregateGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            aggregateProcessor,
            debugDataDumper);
    addGossipManager(aggregateGossipManager);
  }

  void addVoluntaryExitGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    voluntaryExitGossipManager =
        new VoluntaryExitGossipManager(
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            voluntaryExitProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(voluntaryExitGossipManager);
  }

  void addProposerSlashingGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    proposerSlashingGossipManager =
        new ProposerSlashingGossipManager(
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            proposerSlashingProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(proposerSlashingGossipManager);
  }

  void addAttesterSlashingGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    attesterSlashingGossipManager =
        new AttesterSlashingGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            attesterSlashingProcessor,
            debugDataDumper);
    addGossipManager(attesterSlashingGossipManager);
  }

  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    addAttestationGossipManager(forkInfo, forkDigest);
    addBlockGossipManager(forkInfo, forkDigest);
    addAggregateGossipManager(forkInfo, forkDigest);
    addVoluntaryExitGossipManager(forkInfo, forkDigest);
    addProposerSlashingGossipManager(forkInfo, forkDigest);
    addAttesterSlashingGossipManager(forkInfo, forkDigest);
  }

  protected void addGossipManager(final GossipManager gossipManager) {
    gossipManagers.add(gossipManager);
  }

  @Override
  public void stopGossip() {
    gossipManagers.forEach(GossipManager::unsubscribe);
  }

  @Override
  public void stopGossipForOptimisticSync() {
    gossipManagers.stream()
        .filter(manager -> !manager.isEnabledDuringOptimisticSync())
        .forEach(GossipManager::unsubscribe);
  }

  @Override
  public void publishAttestation(final ValidatableAttestation attestation) {
    attestationGossipManager.onNewAttestation(attestation);
    aggregateGossipManager.onNewAggregate(attestation);
  }

  @Override
  public SafeFuture<Void> publishBlock(final SignedBeaconBlock block) {
    return blockGossipManager.publishBlock(block);
  }

  @Override
  public void publishProposerSlashing(final ProposerSlashing message) {
    proposerSlashingGossipManager.publishProposerSlashing(message);
  }

  @Override
  public void publishAttesterSlashing(final AttesterSlashing message) {
    attesterSlashingGossipManager.publishAttesterSlashing(message);
  }

  @Override
  public void publishVoluntaryExit(final SignedVoluntaryExit message) {
    voluntaryExitGossipManager.publishVoluntaryExit(message);
  }

  @Override
  public void subscribeToAttestationSubnetId(final int subnetId) {
    attestationGossipManager.subscribeToSubnetId(subnetId);
  }

  @Override
  public void unsubscribeFromAttestationSubnetId(final int subnetId) {
    attestationGossipManager.unsubscribeFromSubnetId(subnetId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fork", fork)
        .add("activationEpoch", getActivationEpoch())
        .toString();
  }
}
