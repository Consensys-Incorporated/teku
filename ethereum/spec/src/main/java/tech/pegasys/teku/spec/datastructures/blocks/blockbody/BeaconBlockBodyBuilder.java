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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodyBuilder {

  BeaconBlockBodyBuilder randaoReveal(BLSSignature randaoReveal);

  BeaconBlockBodyBuilder eth1Data(Eth1Data eth1Data);

  BeaconBlockBodyBuilder graffiti(Bytes32 graffiti);

  BeaconBlockBodyBuilder attestations(SszList<Attestation> attestations);

  BeaconBlockBodyBuilder proposerSlashings(SszList<ProposerSlashing> proposerSlashings);

  BeaconBlockBodyBuilder attesterSlashings(SszList<AttesterSlashing> attesterSlashings);

  BeaconBlockBodyBuilder deposits(SszList<Deposit> deposits);

  BeaconBlockBodyBuilder voluntaryExits(SszList<SignedVoluntaryExit> voluntaryExits);

  default Boolean supportsSyncAggregate() {
    return false;
  }

  BeaconBlockBodyBuilder syncAggregate(SyncAggregate syncAggregate);

  default Boolean supportsExecutionPayload() {
    return false;
  }

  BeaconBlockBodyBuilder executionPayload(ExecutionPayload executionPayload);

  BeaconBlockBodyBuilder executionPayloadHeader(ExecutionPayloadHeader executionPayloadHeader);

  default Boolean supportsBlsToExecutionChanges() {
    return false;
  }

  BeaconBlockBodyBuilder blsToExecutionChanges(
      SszList<SignedBlsToExecutionChange> blsToExecutionChanges);

  default Boolean supportsKzgCommitments() {
    return false;
  }

  BeaconBlockBodyBuilder blobKzgCommitments(SszList<SszKZGCommitment> blobKzgCommitments);

  default boolean supportsExecutionRequests() {
    return false;
  }

  BeaconBlockBodyBuilder executionRequests(ExecutionRequests executionRequests);

  default Boolean supportsSignedExecutionPayloadBid() {
    return false;
  }

  BeaconBlockBodyBuilder signedExecutionPayloadBid(
      SignedExecutionPayloadBid signedExecutionPayloadBid);

  default Boolean supportsPayloadAttestations() {
    return false;
  }

  BeaconBlockBodyBuilder payloadAttestations(SszList<PayloadAttestation> payloadAttestations);

  default Boolean supportsParentExecutionRequests() {
    return false;
  }

  BeaconBlockBodyBuilder parentExecutionRequests(ExecutionRequests parentExecutionRequests);

  BeaconBlockBody build();
}
