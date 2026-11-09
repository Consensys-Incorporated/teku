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

package tech.pegasys.teku.spec.logic.versions.deneb.block;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.capella.withdrawals.WithdrawalsHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockProcessorDeneb extends BlockProcessorCapella {

  public BlockProcessorDeneb(
      final SpecConfigDeneb specConfig,
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsDeneb schemaDefinitions,
      final WithdrawalsHelpersCapella withdrawalsHelpers) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        SchemaDefinitionsCapella.required(schemaDefinitions),
        withdrawalsHelpers);
  }

  public int getMaxBlobsPerBlock(final BeaconState state) {
    return SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock();
  }

  @Override
  public void validateExecutionPayload(
      final BeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final int maxBlobsPerBlock = getMaxBlobsPerBlock(genericState);
    final SszList<SszKZGCommitment> blobKzgCommitments = extractBlobKzgCommitments(beaconBlockBody);
    if (blobKzgCommitments.size() > maxBlobsPerBlock) {
      throw new BlockProcessingException(
          "Number of kzg commitments in block exceeds max blobs per block");
    }
    super.validateExecutionPayload(genericState, beaconBlockBody, payloadExecutor);
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
    final SszList<SszKZGCommitment> blobKzgCommitments = extractBlobKzgCommitments(beaconBlockBody);
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    return new NewPayloadRequest(executionPayload, versionedHashes, parentBeaconBlockRoot);
  }

  public SszList<SszKZGCommitment> extractBlobKzgCommitments(final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    return beaconBlockBody
        .getOptionalBlobKzgCommitments()
        .orElseThrow(() -> new BlockProcessingException("Blob kzg commitments expected"));
  }
}
