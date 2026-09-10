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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil.ExecutionPayloadProposalData;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionPayloadFactoryGloas implements ExecutionPayloadFactory {

  private final Spec spec;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;

  public ExecutionPayloadFactoryGloas(
      final Spec spec,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager) {
    this.spec = spec;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
  }

  @Override
  public SafeFuture<ExecutionPayloadEnvelope> createUnsignedExecutionPayload(
      final BeaconBlockAndState blockAndState) {
    final UInt64 proposalSlot = blockAndState.getSlot();
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(proposalSlot).getSchemaDefinitions());
    final SafeFuture<ExecutionPayloadProposalData> executionPayloadProposalDataFuture =
        getCachedGetPayloadResponseFuture(proposalSlot)
            .thenApply(
                getPayloadResponse ->
                    new ExecutionPayloadProposalData(
                        getPayloadResponse.getExecutionPayload(),
                        getPayloadResponse.getExecutionRequests().orElseThrow(),
                        schemaDefinitions
                            .getBlobKzgCommitmentsSchema()
                            .createFromBlobsBundle(
                                getPayloadResponse.getBlobsBundle().orElseThrow())));
    return spec.createNewUnsignedExecutionPayload(
        proposalSlot, blockAndState, executionPayloadProposalDataFuture);
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> createDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    final UInt64 slot = signedExecutionPayload.getMessage().getSlot();
    return SafeFuture.<GetPayloadResponse>of(
            () ->
                executionLayerBlockProductionManager
                    .getCachedPayloadResult(slot)
                    .orElseThrow(() -> DataColumnSidecarCreationException.noCachedBlobData(slot))
                    .getPayloadResponseFutureFromLocalFlowRequired())
        .thenApply(
            getPayloadResponse -> {
              if (!getPayloadResponse
                  .getExecutionPayload()
                  .hashTreeRoot()
                  .equals(signedExecutionPayload.getMessage().getPayload().hashTreeRoot())) {
                throw DataColumnSidecarCreationException.cachedPayloadMismatch(slot);
              }
              final BlobsBundle blobsBundle =
                  getPayloadResponse
                      .getBlobsBundle()
                      .orElseThrow(() -> DataColumnSidecarCreationException.noCachedBlobData(slot));
              return createDataColumnSidecars(
                  signedExecutionPayload, blobsBundle.getBlobs(), blobsBundle.getProofs());
            });
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> createDataColumnSidecars(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents) {
    return SafeFuture.completedFuture(
        createDataColumnSidecars(
            signedExecutionPayloadEnvelopeContents.getSignedExecutionPayloadEnvelope(),
            signedExecutionPayloadEnvelopeContents.getBlobs().stream().toList(),
            signedExecutionPayloadEnvelopeContents.getKzgProofs().stream()
                .map(sszKzgProof -> sszKzgProof.getKZGProof())
                .toList()));
  }

  private List<DataColumnSidecar> createDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final List<Blob> blobs,
      final List<KZGProof> proofs) {
    final SpecVersion specVersion = spec.atSlot(signedExecutionPayload.getSlot());
    final int numberOfColumns =
        SpecConfigFulu.required(specVersion.getConfig()).getNumberOfColumns();
    final List<BlobAndCellProofs> blobAndCellProofsList =
        IntStream.range(0, blobs.size())
            .mapToObj(
                index ->
                    new BlobAndCellProofs(
                        blobs.get(index),
                        proofs.stream()
                            .skip((long) index * numberOfColumns)
                            .limit(numberOfColumns)
                            .toList()))
            .toList();
    return MiscHelpersGloas.required(specVersion.miscHelpers())
        .constructDataColumnSidecars(signedExecutionPayload, blobAndCellProofsList);
  }

  // Only local flow for ePBS -> this result has been cached when creating the bid for the block
  private SafeFuture<GetPayloadResponse> getCachedGetPayloadResponseFuture(final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedPayloadResult(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "ExecutionPayloadResult hasn't been cached for slot " + slot))
        .getPayloadResponseFutureFromLocalFlowRequired();
  }
}
