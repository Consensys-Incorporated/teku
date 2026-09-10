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

package tech.pegasys.teku.spec.generator.signatures;

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
import tech.pegasys.teku.spec.signatures.Signer;

public abstract class NoOpSigner implements Signer {

  NoOpSigner() {}

  @Override
  public void delete() {}

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadBid(
      final ExecutionPayloadBid bid, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signProposerPreferences(
      final ProposerPreferences proposerPreferences, final ForkInfo forkInfo) {
    return new SafeFuture<>();
  }

  @Override
  public SafeFuture<BLSSignature> signBuilderRequestAuth(
      final BuilderRequestAuth builderRequestAuth) {
    return new SafeFuture<>();
  }

  @Override
  public abstract Optional<URL> getSigningServiceUrl();
}
