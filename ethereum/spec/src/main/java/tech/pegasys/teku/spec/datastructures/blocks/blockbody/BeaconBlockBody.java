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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public interface BeaconBlockBody extends SszContainer {

  BLSSignature getRandaoReveal();

  SszSignature getRandaoRevealSsz();

  Eth1Data getEth1Data();

  Bytes32 getGraffiti();

  SszBytes32 getGraffitiSsz();

  SszList<ProposerSlashing> getProposerSlashings();

  SszList<AttesterSlashing> getAttesterSlashings();

  SszList<Attestation> getAttestations();

  SszList<Deposit> getDeposits();

  SszList<SignedVoluntaryExit> getVoluntaryExits();

  default Optional<SyncAggregate> getOptionalSyncAggregate() {
    return Optional.empty();
  }

  default Optional<ExecutionPayload> getOptionalExecutionPayload() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadHeader> getOptionalExecutionPayloadHeader() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadSummary> getOptionalExecutionPayloadSummary() {
    return Optional.empty();
  }

  default Optional<SszList<SignedBlsToExecutionChange>> getOptionalBlsToExecutionChanges() {
    return Optional.empty();
  }

  default Optional<SszList<SszKZGCommitment>> getOptionalBlobKzgCommitments() {
    return Optional.empty();
  }

  default Optional<ExecutionRequests> getOptionalExecutionRequests() {
    return Optional.empty();
  }

  default Optional<SignedExecutionPayloadBid> getOptionalSignedExecutionPayloadBid() {
    return Optional.empty();
  }

  default Optional<SszList<PayloadAttestation>> getOptionalPayloadAttestations() {
    return Optional.empty();
  }

  default Optional<ExecutionRequests> getOptionalParentExecutionRequests() {
    return Optional.empty();
  }

  default boolean isBlinded() {
    return false;
  }

  @Override
  BeaconBlockBodySchema<? extends BeaconBlockBody> getSchema();

  default Optional<BeaconBlockBodyAltair> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyBellatrix> toBlindedVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyCapella> toBlindedVersionCapella() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyElectra> toVersionElectra() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyDeneb> toBlindedVersionDeneb() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodyElectra> toBlindedVersionElectra() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodyGloas> toVersionGloas() {
    return Optional.empty();
  }
}
