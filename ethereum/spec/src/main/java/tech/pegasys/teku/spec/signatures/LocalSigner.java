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
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
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
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;

public class LocalSigner implements Signer {

  private final Spec spec;
  private final BLSKeyPair keypair;
  private final AsyncRunner asyncRunner;
  private final SigningRootUtil signingRootUtil;

  public LocalSigner(final Spec spec, final BLSKeyPair keypair, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.keypair = keypair;
    this.asyncRunner = asyncRunner;
    this.signingRootUtil = new SigningRootUtil(spec);
  }

  @Override
  public void delete() {
    keypair.getSecretKey().destroy();
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForRandaoReveal(epoch, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignAggregationSlot(slot, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignVoluntaryExit(voluntaryExit, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            slot,
            utils ->
                utils.getSyncCommitteeMessageSigningRoot(
                    beaconBlockRoot, spec.computeEpochAtSlot(slot), forkInfo))
        .thenCompose(this::sign);
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            selectionData.getSlot(),
            utils -> utils.getSyncAggregatorSelectionDataSigningRoot(selectionData, forkInfo))
        .thenCompose(this::sign);
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return signingRootFromSyncCommitteeUtils(
            contributionAndProof.getContribution().getSlot(),
            utils -> utils.getContributionAndProofSigningRoot(contributionAndProof, forkInfo))
        .thenCompose(this::sign);
  }

  @Override
  public SafeFuture<BLSSignature> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    return sign(signingRootUtil.signingRootForValidatorRegistration(validatorRegistration));
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadBid(
      final ExecutionPayloadBid bid, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignExecutionPayloadBid(bid, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return sign(signingRootUtil.signingRootForSignExecutionPayloadEnvelope(envelope, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForSignPayloadAttestationData(payloadAttestationData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signProposerPreferences(
      final ProposerPreferences proposerPreferences, final ForkInfo forkInfo) {
    return sign(
        signingRootUtil.signingRootForSignProposerPreferences(proposerPreferences, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signBuilderRequestAuth(
      final BuilderRequestAuth builderRequestAuth) {
    return sign(signingRootUtil.signingRootForSignBuilderRequestAuth(builderRequestAuth));
  }

  private SafeFuture<Bytes> signingRootFromSyncCommitteeUtils(
      final UInt64 slot, final Function<SyncCommitteeUtil, Bytes> createSigningRoot) {
    return SafeFuture.of(() -> createSigningRoot.apply(spec.getSyncCommitteeUtilRequired(slot)));
  }

  @Override
  public Optional<URL> getSigningServiceUrl() {
    return Optional.empty();
  }

  private SafeFuture<BLSSignature> sign(final Bytes signingRoot) {
    return asyncRunner.runAsync(
        () -> SafeFuture.completedFuture(BLS.sign(keypair.getSecretKey(), signingRoot)));
  }
}
