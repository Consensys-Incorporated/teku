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

package tech.pegasys.teku.spec.signatures;

import java.net.URL;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public interface Signer {

  void delete();

  SafeFuture<BLSSignature> createRandaoReveal(UInt64 epoch, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signBlock(BeaconBlock block, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAttestationData(AttestationData attestationData, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAggregationSlot(UInt64 slot, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signAggregateAndProof(
      AggregateAndProof aggregateAndProof, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signVoluntaryExit(VoluntaryExit voluntaryExit, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signSyncCommitteeMessage(
      UInt64 slot, Bytes32 beaconBlockRoot, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      SyncAggregatorSelectionData selectionData, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signContributionAndProof(
      ContributionAndProof contributionAndProof, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signValidatorRegistration(ValidatorRegistration validatorRegistration);

  SafeFuture<BLSSignature> signExecutionPayloadBid(ExecutionPayloadBid bid, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      ExecutionPayloadEnvelope envelope, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signPayloadAttestationData(
      PayloadAttestationData payloadAttestationData, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signProposerPreferences(
      ProposerPreferences proposerPreferences, ForkInfo forkInfo);

  SafeFuture<BLSSignature> signBuilderRequestAuth(BuilderRequestAuth builderRequestAuth);

  default boolean isLocal() {
    return getSigningServiceUrl().isEmpty();
  }

  Optional<URL> getSigningServiceUrl();
}
