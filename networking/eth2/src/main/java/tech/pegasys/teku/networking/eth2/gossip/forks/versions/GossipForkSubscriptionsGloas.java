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

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadBidGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PqPayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerPreferencesGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsGloas extends GossipForkSubscriptionsFulu {

  private static final Logger LOG = LogManager.getLogger();

  private final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor;
  private final OperationProcessor<PayloadAttestationMessage> payloadAttestationMessageProcessor;
  private final OperationProcessor<PayloadAttestationMessagePq>
      payloadAttestationMessagePqProcessor;
  private final OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidProcessor;
  private final OperationProcessor<SignedProposerPreferences> proposerPreferencesProcessor;

  private ExecutionPayloadGossipManager executionPayloadGossipManager;
  private PayloadAttestationMessageGossipManager payloadAttestationMessageGossipManager;
  private ExecutionPayloadBidGossipManager executionPayloadBidGossipManager;
  private ProposerPreferencesGossipManager proposerPreferencesGossipManager;

  public GossipForkSubscriptionsGloas(
      final Fork fork,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final DiscoveryNetwork<?> discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final OperationProcessor<SignedBeaconBlock> blockProcessor,
      final OperationProcessor<BlobSidecar> blobSidecarProcessor,
      final OperationProcessor<ValidatableAttestation> attestationProcessor,
      final OperationProcessor<ValidatableAttestation> aggregateProcessor,
      final OperationProcessor<AttesterSlashing> attesterSlashingProcessor,
      final OperationProcessor<ProposerSlashing> proposerSlashingProcessor,
      final OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor,
      final OperationProcessor<SignedContributionAndProof>
          signedContributionAndProofOperationProcessor,
      final OperationProcessor<ValidatableSyncCommitteeMessage>
          syncCommitteeMessageOperationProcessor,
      final Optional<OperationProcessor<LightClientFinalityUpdate>>
          lightClientFinalityUpdateProcessor,
      final Optional<OperationProcessor<LightClientOptimisticUpdate>>
          lightClientOptimisticUpdateProcessor,
      final OperationProcessor<SignedBlsToExecutionChange>
          signedBlsToExecutionChangeOperationProcessor,
      final OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor,
      final OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadOperationProcessor,
      final OperationProcessor<PayloadAttestationMessage>
          payloadAttestationMessageOperationProcessor,
      final OperationProcessor<PayloadAttestationMessagePq>
          payloadAttestationMessagePqOperationProcessor,
      final OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidOperationProcessor,
      final OperationProcessor<SignedProposerPreferences> proposerPreferencesOperationProcessor,
      final DebugDataDumper debugDataDumper,
      final DasGossipLogger dasGossipLogger,
      final OperationProcessor<ExecutionProof> executionProcessorOperationProcessor,
      final boolean isExecutionProofTopicEnabled,
      final Supplier<Boolean> isSuperNodeSupplier) {
    super(
        fork,
        spec,
        asyncRunner,
        metricsSystem,
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        blockProcessor,
        blobSidecarProcessor,
        attestationProcessor,
        aggregateProcessor,
        attesterSlashingProcessor,
        proposerSlashingProcessor,
        voluntaryExitProcessor,
        signedContributionAndProofOperationProcessor,
        syncCommitteeMessageOperationProcessor,
        lightClientFinalityUpdateProcessor,
        lightClientOptimisticUpdateProcessor,
        signedBlsToExecutionChangeOperationProcessor,
        dataColumnSidecarOperationProcessor,
        debugDataDumper,
        dasGossipLogger,
        executionProcessorOperationProcessor,
        isExecutionProofTopicEnabled,
        isSuperNodeSupplier);
    this.executionPayloadProcessor = executionPayloadOperationProcessor;
    this.payloadAttestationMessageProcessor = payloadAttestationMessageOperationProcessor;
    this.payloadAttestationMessagePqProcessor = payloadAttestationMessagePqOperationProcessor;
    this.executionPayloadBidProcessor = executionPayloadBidOperationProcessor;
    this.proposerPreferencesProcessor = proposerPreferencesOperationProcessor;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    if (isPq()) {
      addPqGossipManagers(forkInfo, forkDigest);
      return;
    }
    super.addGossipManagers(forkInfo, forkDigest);
    addExecutionPayloadGossipManager(forkInfo, forkDigest);
    addPayloadAttestationMessageGossipManager(forkInfo, forkDigest);
    addExecutionPayloadBidGossipManager(forkInfo, forkDigest);
    addProposerPreferencesGossipManager(forkInfo, forkDigest);
  }

  /**
   * PQ (and Heze, which builds on it) keeps only the topics whose messages it has. Voluntary exits,
   * BLS to execution changes, sync committee messages and contributions, and the light client
   * topics are removed (PQ p2p-interface.md).
   *
   * <p>Aggregates and payload attestation messages are PQ's own types, with their own gossip
   * managers.
   */
  private void addPqGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    addAttestationGossipManager(forkInfo, forkDigest);
    addBlockGossipManager(forkInfo, forkDigest);
    addPqAggregateGossipManager(forkInfo, forkDigest);
    addPqPayloadAttestationMessageGossipManager(forkInfo, forkDigest);
    addProposerSlashingGossipManager(forkInfo, forkDigest);
    addAttesterSlashingGossipManager(forkInfo, forkDigest);
    addDataColumnSidecarGossipManager(forkInfo, forkDigest);
    addExecutionPayloadGossipManager(forkInfo, forkDigest);
    addExecutionPayloadBidGossipManager(forkInfo, forkDigest);
    addProposerPreferencesGossipManager(forkInfo, forkDigest);
  }

  private boolean isPq() {
    return spec.atEpoch(getActivationEpoch())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.PQ);
  }

  // PQ has no voluntary exit or sync committee topics, so their gossip managers are never created.
  // Subnet subscriptions still arrive for every fork, and a message may still be published at the
  // fork boundary, so these are ignored rather than sent to a missing manager.

  @Override
  public void publishVoluntaryExit(final SignedVoluntaryExit message) {
    if (isPq()) {
      LOG.debug("Not publishing voluntary exit: PQ has no voluntary exit topic");
      return;
    }
    super.publishVoluntaryExit(message);
  }

  @Override
  public void publishSyncCommitteeMessage(final ValidatableSyncCommitteeMessage message) {
    if (isPq()) {
      LOG.debug("Not publishing sync committee message: PQ has no sync committee topics");
      return;
    }
    super.publishSyncCommitteeMessage(message);
  }

  @Override
  public void publishSyncCommitteeContribution(final SignedContributionAndProof message) {
    if (isPq()) {
      LOG.debug("Not publishing sync committee contribution: PQ has no sync committee topics");
      return;
    }
    super.publishSyncCommitteeContribution(message);
  }

  @Override
  public void subscribeToSyncCommitteeSubnet(final int subnetId) {
    if (!isPq()) {
      super.subscribeToSyncCommitteeSubnet(subnetId);
    }
  }

  @Override
  public void unsubscribeFromSyncCommitteeSubnet(final int subnetId) {
    if (!isPq()) {
      super.unsubscribeFromSyncCommitteeSubnet(subnetId);
    }
  }

  void addExecutionPayloadGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.executionPayloadGossipManager =
        new ExecutionPayloadGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            executionPayloadProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(executionPayloadGossipManager);
  }

  void addPayloadAttestationMessageGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.payloadAttestationMessageGossipManager =
        new PayloadAttestationMessageGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            payloadAttestationMessageProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(payloadAttestationMessageGossipManager);
  }

  void addPqPayloadAttestationMessageGossipManager(
      final ForkInfo forkInfo, final Bytes4 forkDigest) {
    addGossipManager(
        new PqPayloadAttestationMessageGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            payloadAttestationMessagePqProcessor,
            debugDataDumper));
  }

  void addExecutionPayloadBidGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.executionPayloadBidGossipManager =
        new ExecutionPayloadBidGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            executionPayloadBidProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(executionPayloadBidGossipManager);
  }

  void addProposerPreferencesGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    this.proposerPreferencesGossipManager =
        new ProposerPreferencesGossipManager(
            spec,
            recentChainData,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            proposerPreferencesProcessor,
            spec.getNetworkingConfig(),
            debugDataDumper);
    addGossipManager(proposerPreferencesGossipManager);
  }

  @Override
  public SafeFuture<Void> publishExecutionPayload(final SignedExecutionPayloadEnvelope message) {
    return executionPayloadGossipManager.publish(message);
  }

  @Override
  public void publishPayloadAttestationMessage(final PayloadAttestationMessage message) {
    payloadAttestationMessageGossipManager.publish(message);
  }

  @Override
  public void publishExecutionPayloadBid(final SignedExecutionPayloadBid message) {
    executionPayloadBidGossipManager.publish(message);
  }

  @Override
  public void publishProposerPreferences(final SignedProposerPreferences message) {
    proposerPreferencesGossipManager.publish(message);
  }
}
