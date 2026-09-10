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
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
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

/**
 * A wrapper for a {@link Signer} which adds slashing protection.
 *
 * <p>A validator can only be slashed because of the blocks or attestations they sign, so all other
 * methods are delegated without any checks.
 *
 * <p>Blocks and attestations check with the specified {@link SlashingProtector} before signing and
 * throw {@link SlashableConditionException} if signing is disallowed.
 */
public class SlashingProtectedSigner implements Signer {

  private final BLSPublicKey validatorPublicKey;
  private final SlashingProtector slashingProtector;
  private final Signer delegate;

  public SlashingProtectedSigner(
      final BLSPublicKey validatorPublicKey,
      final SlashingProtector slashingProtector,
      final Signer delegate) {
    this.validatorPublicKey = validatorPublicKey;
    this.slashingProtector = slashingProtector;
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return slashingProtector
        .maySignBlock(validatorPublicKey, forkInfo.getGenesisValidatorsRoot(), block.getSlot())
        .thenAccept(verifySigningAllowed(slashableBlockMessage(block)))
        .thenCompose(__ -> delegate.signBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return slashingProtector
        .maySignAttestation(
            validatorPublicKey,
            forkInfo.getGenesisValidatorsRoot(),
            attestationData.getSource().getEpoch(),
            attestationData.getTarget().getEpoch())
        .thenAccept(verifySigningAllowed(slashableAttestationMessage(attestationData)))
        .thenCompose(__ -> delegate.signAttestationData(attestationData, forkInfo));
  }

  private Supplier<String> slashableBlockMessage(final BeaconBlock block) {
    return () ->
        "Refusing to sign block at slot "
            + block.getSlot()
            + " as it may violate a slashing condition";
  }

  private Supplier<String> slashableAttestationMessage(final AttestationData attestationData) {
    return () ->
        "Refusing to sign attestation at slot "
            + attestationData.getSlot()
            + " with source epoch "
            + attestationData.getSource().getEpoch()
            + " and target epoch "
            + attestationData.getTarget().getEpoch()
            + " because it may violate a slashing condition";
  }

  private Consumer<Boolean> verifySigningAllowed(final Supplier<String> messageSupplier) {
    return maySign -> {
      if (!maySign) {
        throw new SlashableConditionException(messageSupplier.get());
      }
    };
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return delegate.createRandaoReveal(epoch, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return delegate.signAggregationSlot(slot, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return delegate.signAggregateAndProof(aggregateAndProof, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return delegate.signVoluntaryExit(voluntaryExit, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return delegate.signSyncCommitteeMessage(slot, beaconBlockRoot, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    return delegate.signSyncCommitteeSelectionProof(selectionData, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return delegate.signContributionAndProof(contributionAndProof, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    return delegate.signValidatorRegistration(validatorRegistration);
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadBid(
      final ExecutionPayloadBid bid, final ForkInfo forkInfo) {
    return delegate.signExecutionPayloadBid(bid, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return delegate.signExecutionPayloadEnvelope(envelope, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return delegate.signPayloadAttestationData(payloadAttestationData, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signProposerPreferences(
      final ProposerPreferences proposerPreferences, final ForkInfo forkInfo) {
    return delegate.signProposerPreferences(proposerPreferences, forkInfo);
  }

  @Override
  public SafeFuture<BLSSignature> signBuilderRequestAuth(
      final BuilderRequestAuth builderRequestAuth) {
    return delegate.signBuilderRequestAuth(builderRequestAuth);
  }

  @Override
  public Optional<URL> getSigningServiceUrl() {
    return delegate.getSigningServiceUrl();
  }

  @Override
  public void delete() {
    delegate.delete();
  }
}
