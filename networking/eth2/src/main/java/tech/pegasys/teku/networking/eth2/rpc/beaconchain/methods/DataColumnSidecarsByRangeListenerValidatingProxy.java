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

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType.DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType.DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER;
import static tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM;
import static tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator.DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

public class DataColumnSidecarsByRangeListenerValidatingProxy
    extends AbstractDataColumnSidecarValidator implements RpcResponseListener<DataColumnSidecar> {
  private final RpcResponseListener<DataColumnSidecar> dataColumnSidecarResponseListener;

  private final UInt64 startSlot;
  private final UInt64 endSlot;

  private final Set<UInt64> columns;
  private final MetricsHistogram dataColumnSidecarInclusionProofVerificationTimeSeconds;
  private final MetricsHistogram dataColumnSidecarKzgBatchVerificationTimeSeconds;

  public DataColumnSidecarsByRangeListenerValidatingProxy(
      final Spec spec,
      final Peer peer,
      final RpcResponseListener<DataColumnSidecar> dataColumnSidecarResponseListener,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator,
      final UInt64 startSlot,
      final UInt64 count,
      final List<UInt64> columns,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider) {
    super(peer, spec, dataColumnSidecarSignatureValidator, blobKzgCommitmentsProvider);
    this.dataColumnSidecarResponseListener = dataColumnSidecarResponseListener;
    this.startSlot = startSlot;
    this.endSlot = startSlot.plus(count).minusMinZero(1);
    this.columns = new HashSet<>(columns);
    this.dataColumnSidecarInclusionProofVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM.apply(
            metricsSystem, timeProvider);
    this.dataColumnSidecarKzgBatchVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM.apply(metricsSystem, timeProvider);
  }

  @Override
  public SafeFuture<?> onResponse(final DataColumnSidecar dataColumnSidecar) {
    return SafeFuture.of(
        () -> {
          final UInt64 dataColumnSidecarSlot = dataColumnSidecar.getSlot();
          if (!dataColumnSidecarSlotIsInRange(dataColumnSidecarSlot)) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer, DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE);
          }

          if (!columns.contains(dataColumnSidecar.getIndex())) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer, DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER);
          }
          if (!verifyValidity(dataColumnSidecar)) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer,
                DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                    .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED);
          }

          final boolean inclusionProofValid;
          try (MetricsHistogram.Timer ignored =
              dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
            inclusionProofValid = verifyInclusionProof(dataColumnSidecar);
          } catch (final IOException ioException) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer,
                DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                    .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED);
          }
          if (!inclusionProofValid) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer,
                DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                    .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED);
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
                  maybeKzgProofsVerificationResult -> {
                    if (maybeKzgProofsVerificationResult.isPresent()) {
                      throw new DataColumnSidecarsResponseInvalidResponseException(
                          peer,
                          InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED,
                          maybeKzgProofsVerificationResult.get());
                    }
                    return verifySignature(dataColumnSidecar);
                  })
              .thenCompose(
                  signatureIsValid -> {
                    if (signatureIsValid) {
                      return SafeFuture.COMPLETE;
                    }
                    return SafeFuture.failedFuture(
                        new DataColumnSidecarsResponseInvalidResponseException(
                            peer,
                            InvalidResponseType.DATA_COLUMN_SIDECAR_HEADER_INVALID_SIGNATURE));
                  })
              .thenCompose(__ -> dataColumnSidecarResponseListener.onResponse(dataColumnSidecar));
        });
  }

  private boolean dataColumnSidecarSlotIsInRange(final UInt64 dataColumnSidecarSlot) {
    return dataColumnSidecarSlot.isGreaterThanOrEqualTo(startSlot)
        && dataColumnSidecarSlot.isLessThanOrEqualTo(endSlot);
  }
}
