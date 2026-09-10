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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.StampedLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFutureSupplier;
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

public class DeletableSigner implements Signer {
  private final Signer delegate;
  private boolean deleted = false;
  private final ReadWriteLock lock = new StampedLock().asReadWriteLock();
  private final Lock readLock = lock.readLock();

  public DeletableSigner(final Signer delegate) {
    this.delegate = delegate;
  }

  @Override
  public void delete() {
    lock.writeLock().lock();
    try {
      deleted = true;
      delegate.delete();
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return sign(() -> delegate.createRandaoReveal(epoch, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return sign(() -> delegate.signBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return sign(() -> delegate.signAttestationData(attestationData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return sign(() -> delegate.signAggregationSlot(slot, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return sign(() -> delegate.signAggregateAndProof(aggregateAndProof, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return sign(() -> delegate.signVoluntaryExit(voluntaryExit, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final ForkInfo forkInfo) {
    return sign(() -> delegate.signSyncCommitteeMessage(slot, beaconBlockRoot, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signSyncCommitteeSelectionProof(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    return sign(() -> delegate.signSyncCommitteeSelectionProof(selectionData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signContributionAndProof(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    return sign(() -> delegate.signContributionAndProof(contributionAndProof, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signValidatorRegistration(
      final ValidatorRegistration validatorRegistration) {
    return sign(() -> delegate.signValidatorRegistration(validatorRegistration));
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadBid(
      final ExecutionPayloadBid bid, final ForkInfo forkInfo) {
    return sign(() -> delegate.signExecutionPayloadBid(bid, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return sign(() -> delegate.signExecutionPayloadEnvelope(envelope, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return sign(() -> delegate.signPayloadAttestationData(payloadAttestationData, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signProposerPreferences(
      final ProposerPreferences proposerPreferences, final ForkInfo forkInfo) {
    return sign(() -> delegate.signProposerPreferences(proposerPreferences, forkInfo));
  }

  @Override
  public SafeFuture<BLSSignature> signBuilderRequestAuth(
      final BuilderRequestAuth builderRequestAuth) {
    return sign(() -> delegate.signBuilderRequestAuth(builderRequestAuth));
  }

  @Override
  public Optional<URL> getSigningServiceUrl() {
    return delegate.getSigningServiceUrl();
  }

  private SafeFuture<BLSSignature> sign(
      final ExceptionThrowingFutureSupplier<BLSSignature> supplier) {
    readLock.lock();
    final SafeFuture<BLSSignature> future =
        deleted ? SafeFuture.failedFuture(new SignerNotActiveException()) : SafeFuture.of(supplier);

    return future.alwaysRun(readLock::unlock);
  }
}
