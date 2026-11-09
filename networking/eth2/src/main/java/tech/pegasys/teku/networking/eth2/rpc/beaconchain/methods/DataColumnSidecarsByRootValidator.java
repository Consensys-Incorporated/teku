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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;
import static tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM;
import static tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator.DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

public class DataColumnSidecarsByRootValidator extends AbstractDataColumnSidecarValidator {
  private final Set<DataColumnIdentifier> expectedDataColumnIdentifiers;
  private final MetricsHistogram dataColumnSidecarInclusionProofVerificationTimeSeconds;
  private final MetricsHistogram dataColumnSidecarKzgBatchVerificationTimeSeconds;

  public DataColumnSidecarsByRootValidator(
      final Peer peer,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator,
      final List<DataColumnIdentifier> expectedDataColumnIdentifiers,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider) {
    super(peer, spec, dataColumnSidecarSignatureValidator, blobKzgCommitmentsProvider);
    this.expectedDataColumnIdentifiers = ConcurrentHashMap.newKeySet();
    this.expectedDataColumnIdentifiers.addAll(expectedDataColumnIdentifiers);
    this.dataColumnSidecarInclusionProofVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM.apply(
            metricsSystem, timeProvider);
    this.dataColumnSidecarKzgBatchVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM.apply(metricsSystem, timeProvider);
  }

  public SafeFuture<Void> validate(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnIdentifier dataColumnIdentifier =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar);
    if (!expectedDataColumnIdentifiers.remove(dataColumnIdentifier)) {
      return SafeFuture.failedFuture(
          new DataColumnSidecarsResponseInvalidResponseException(
              peer, InvalidResponseType.DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER));
    }

    if (!verifyValidity(dataColumnSidecar)) {
      return SafeFuture.failedFuture(
          new DataColumnSidecarsResponseInvalidResponseException(
              peer,
              DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                  .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED));
    }

    final boolean inclusionProofValid;
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
      inclusionProofValid = verifyInclusionProof(dataColumnSidecar);
    } catch (final IOException e) {
      return SafeFuture.failedFuture(
          new DataColumnSidecarsResponseInvalidResponseException(
              peer, InvalidResponseType.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED));
    }
    if (!inclusionProofValid) {
      return SafeFuture.failedFuture(
          new DataColumnSidecarsResponseInvalidResponseException(
              peer, InvalidResponseType.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED));
    }

    final MetricsHistogram.Timer kzgVerificationTimer =
        dataColumnSidecarKzgBatchVerificationTimeSeconds.startTimer();
    return verifyKzgProofs(dataColumnSidecar)
        .alwaysRun(kzgVerificationTimer.closeUnchecked())
        .exceptionallyCompose(
            error ->
                SafeFuture.failedFuture(
                    new DataColumnSidecarsResponseInvalidResponseException(
                        peer,
                        InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED,
                        error)))
        .thenCompose(
            maybeKzgProofsValidationResult -> {
              if (maybeKzgProofsValidationResult.isPresent()) {
                return SafeFuture.failedFuture(
                    new DataColumnSidecarsResponseInvalidResponseException(
                        peer,
                        InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED,
                        maybeKzgProofsValidationResult.get()));
              }
              return SafeFuture.COMPLETE;
            });
  }
}
