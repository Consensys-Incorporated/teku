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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionProofGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.ExecutionProofSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsElectra extends GossipForkSubscriptionsDeneb {

  final OperationProcessor<ExecutionProof> executionProofOperationProcessor;

  private Optional<ExecutionProofGossipManager> executionProofGossipManager;
  private final boolean isExecutionProofTopicEnabled;
  private static final Logger LOG = LogManager.getLogger();

  public GossipForkSubscriptionsElectra(
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
      final DebugDataDumper debugDataDumper,
      final OperationProcessor<ExecutionProof> executionProofOperationProcessor,
      final boolean isExecutionProofTopicEnabled) {
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
        debugDataDumper);
    this.executionProofOperationProcessor = executionProofOperationProcessor;
    this.isExecutionProofTopicEnabled = isExecutionProofTopicEnabled;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    super.addGossipManagers(forkInfo, forkDigest);
    addExecutionProofGossipManager(forkInfo, forkDigest);
  }

  private void addExecutionProofGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    if (isExecutionProofTopicEnabled) {
      LOG.debug("Creating ExecutionProofSubnetSubscriptions");
      ExecutionProofSubnetSubscriptions executionProofSubnetSubscriptions =
          new ExecutionProofSubnetSubscriptions(
              spec,
              asyncRunner,
              discoveryNetwork,
              gossipEncoding,
              recentChainData,
              executionProofOperationProcessor,
              debugDataDumper,
              forkInfo,
              forkDigest);

      executionProofGossipManager =
          Optional.of(new ExecutionProofGossipManager(executionProofSubnetSubscriptions));
      addGossipManager(executionProofGossipManager.get());
    } else {
      LOG.debug("Using ExecutionProofGossipManager.NOOP");
      executionProofGossipManager = Optional.empty();
    }
  }

  @Override
  public void publishExecutionProof(final ExecutionProof executionProof) {
    executionProofGossipManager.ifPresent(
        epGossipManager -> epGossipManager.publish(executionProof));
  }

  @Override
  public void subscribeToExecutionProofSubnet(final int subnetId) {
    executionProofGossipManager.ifPresent(
        epGossipManager -> epGossipManager.subscribeToSubnetId(subnetId));
  }

  @Override
  public void unsubscribeFromExecutionProofSubnet(final int subnetId) {
    executionProofGossipManager.ifPresent(
        epGossipManager -> epGossipManager.unsubscribeFromSubnetId(subnetId));
  }
}
