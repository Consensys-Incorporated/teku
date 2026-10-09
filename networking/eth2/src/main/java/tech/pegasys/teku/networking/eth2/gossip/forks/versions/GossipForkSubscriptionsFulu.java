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
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
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
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsFulu extends GossipForkSubscriptionsElectra {

  private final OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor;
  private DataColumnSidecarGossipManager dataColumnSidecarGossipManager;
  public DasGossipLogger dasGossipLogger;
  private final Supplier<Boolean> isSuperNodeSupplier;

  public GossipForkSubscriptionsFulu(
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
      final DebugDataDumper debugDataDumper,
      final DasGossipLogger dasGossipLogger,
      final OperationProcessor<ExecutionProof> executionProofOperationProcessor,
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
        debugDataDumper,
        executionProofOperationProcessor,
        isExecutionProofTopicEnabled);
    this.dataColumnSidecarOperationProcessor = dataColumnSidecarOperationProcessor;
    this.dasGossipLogger = dasGossipLogger;
    this.isSuperNodeSupplier = isSuperNodeSupplier;
  }

  @Override
  protected void addGossipManagers(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    super.addGossipManagers(forkInfo, forkDigest);
    addDataColumnSidecarGossipManager(forkInfo, forkDigest);
  }

  void addDataColumnSidecarGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    final DataColumnSidecarSubnetSubscriptions dataColumnSidecarSubnetSubscriptions =
        new DataColumnSidecarSubnetSubscriptions(
            spec,
            asyncRunner,
            discoveryNetwork,
            gossipEncoding,
            recentChainData,
            dataColumnSidecarOperationProcessor,
            debugDataDumper,
            forkInfo,
            forkDigest);

    this.dataColumnSidecarGossipManager =
        new DataColumnSidecarGossipManager(
            dataColumnSidecarSubnetSubscriptions, dasGossipLogger, isSuperNodeSupplier);

    addGossipManager(dataColumnSidecarGossipManager);
  }

  @Override
  protected void addBlobSidecarGossipManager(final ForkInfo forkInfo, final Bytes4 forkDigest) {
    // Do nothing
  }

  @Override
  public SafeFuture<Void> publishBlobSidecar(final BlobSidecar blobSidecar) {
    // Do nothing
    return SafeFuture.COMPLETE;
  }

  @Override
  public void publishDataColumnSidecar(final DataColumnSidecar blobSidecar) {
    dataColumnSidecarGossipManager.publish(blobSidecar);
  }

  @Override
  public void subscribeToDataColumnSidecarSubnet(final int subnetId) {
    dataColumnSidecarGossipManager.subscribeToSubnetId(subnetId);
  }

  @Override
  public void unsubscribeFromDataColumnSidecarSubnet(final int subnetId) {
    dataColumnSidecarGossipManager.unsubscribeFromSubnetId(subnetId);
  }
}
