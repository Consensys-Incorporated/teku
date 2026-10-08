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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
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

public interface Eth2P2PNetwork extends P2PNetwork<Eth2Peer> {

  void onEpoch(UInt64 epoch);

  void onSyncStateChanged(final boolean isCloseToInSync, final boolean isOptimistic);

  void subscribeToAttestationSubnetId(int subnetId);

  void unsubscribeFromAttestationSubnetId(int subnetId);

  void setLongTermAttestationSubnetSubscriptions(Iterable<Integer> subnetIndices);

  void subscribeToSyncCommitteeSubnetId(int subnetId);

  void unsubscribeFromSyncCommitteeSubnetId(int subnetId);

  void subscribeToDataColumnSidecarSubnetId(int subnetId);

  void unsubscribeFromDataColumnSidecarSubnetId(int subnetId);

  void subscribeToExecutionProofSubnetId(int subnetId);

  void unsubscribeFromExecutionProofSubnetId(int subnetId);

  MetadataMessage getMetadata();

  void publishSyncCommitteeMessage(ValidatableSyncCommitteeMessage message);

  void publishSyncCommitteeContribution(SignedContributionAndProof signedContributionAndProof);

  void publishProposerSlashing(ProposerSlashing proposerSlashing);

  void publishAttesterSlashing(AttesterSlashing attesterSlashing);

  void publishVoluntaryExit(SignedVoluntaryExit signedVoluntaryExit);

  void publishSignedBlsToExecutionChange(SignedBlsToExecutionChange signedBlsToExecutionChange);

  void publishPayloadAttestationMessage(PayloadAttestationMessage payloadAttestationMessage);

  void publishExecutionPayloadBid(SignedExecutionPayloadBid executionPayloadBid);

  void publishProposerPreferences(SignedProposerPreferences signedProposerPreferences);

  void publishLightClientFinalityUpdate(LightClientFinalityUpdate finalityUpdate);

  void publishLightClientOptimisticUpdate(LightClientOptimisticUpdate optimisticUpdate);
}
