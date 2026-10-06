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

package tech.pegasys.teku.spec.logic.versions.bellatrix.block;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class BlockProcessorBellatrix extends BlockProcessorAltair {
  private final MiscHelpersBellatrix miscHelpersBellatrix;
  private final SchemaDefinitionsBellatrix schemaDefinitions;

  public BlockProcessorBellatrix(
      final SpecConfigBellatrix specConfig,
      final Predicates predicates,
      final MiscHelpersBellatrix miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsBellatrix schemaDefinitions) {
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
        operationValidator);
    this.miscHelpersBellatrix = miscHelpers;
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final MutableBeaconStateBellatrix state = MutableBeaconStateBellatrix.required(genericState);
    final BeaconBlockBody blockBody = block.getBody();

    if (!(blockBody instanceof BeaconBlockBodyBellatrix
        || blockBody instanceof BlindedBeaconBlockBodyBellatrix)) {
      throw new BlockProcessingException(
          "The received block has no execution payload, and cannot be processed.");
    }

    final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier =
        getValidatorExitContextSupplier(state);

    final UInt64 parentSlot = state.getLatestBlockHeader().getSlot();

    processParentExecutionPayload(state, block, validatorExitContextSupplier);
    processBlockHeader(state, block);

    if (miscHelpersBellatrix.isExecutionEnabled(genericState, block)) {
      // Bellatrix -> process_execution_payload
      // Capella -> process_execution_payload + process_withdrawals
      // Gloas -> process_withdrawals + process_execution_payload_bid
      executionProcessing(genericState, block, payloadExecutor);
    }
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(
        state, block.getBody(), indexedAttestationCache, validatorExitContextSupplier, parentSlot);
    processSyncAggregate(
        state, blockBody.getOptionalSyncAggregate().orElseThrow(), signatureVerifier);
  }

  public void executionProcessing(
      final MutableBeaconState genericState,
      final BeaconBlock beaconBlock,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    processExecutionPayload(genericState, beaconBlock.getBody(), payloadExecutor);
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {

    validateExecutionPayload(genericState, beaconBlockBody, payloadExecutor);

    final MutableBeaconStateBellatrix state = MutableBeaconStateBellatrix.required(genericState);
    final ExecutionPayloadHeader executionPayloadHeader =
        extractExecutionPayloadHeader(beaconBlockBody);
    state.setLatestExecutionPayloadHeader(executionPayloadHeader);
  }

  public ExecutionPayloadHeader extractExecutionPayloadHeader(final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    if (beaconBlockBody.isBlinded()) {
      return beaconBlockBody.getOptionalExecutionPayloadHeader().orElseThrow();
    } else {
      final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
      return schemaDefinitions
          .getExecutionPayloadHeaderSchema()
          .createFromExecutionPayload(executionPayload);
    }
  }

  public ExecutionPayload extractExecutionPayload(final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    return beaconBlockBody
        .getOptionalExecutionPayload()
        .orElseThrow(() -> new BlockProcessingException("Execution payload expected"));
  }

  @Override
  public void validateExecutionPayload(
      final BeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final BeaconStateBellatrix state = BeaconStateBellatrix.required(genericState);
    final ExecutionPayloadHeader executionPayloadHeader =
        extractExecutionPayloadHeader(beaconBlockBody);
    validateExecutionPayloadHeader(state, executionPayloadHeader);

    if (payloadExecutor.isPresent()) {
      final NewPayloadRequest payloadToExecute =
          computeNewPayloadRequest(genericState, beaconBlockBody);
      final boolean optimisticallyAccept =
          payloadExecutor
              .get()
              .optimisticallyExecute(state.getLatestExecutionPayloadHeader(), payloadToExecute);
      if (!optimisticallyAccept) {
        throw new BlockProcessingException("Execution payload was not optimistically accepted");
      }
    }
  }

  @Override
  public void validateExecutionPayloadHeader(
      final BeaconState genericState, final ExecutionPayloadHeader executionPayloadHeader)
      throws BlockProcessingException {
    final BeaconStateBellatrix state = BeaconStateBellatrix.required(genericState);
    if (miscHelpersBellatrix.isMergeTransitionComplete(state)) {
      if (!executionPayloadHeader
          .getParentHash()
          .equals(state.getLatestExecutionPayloadHeaderRequired().getBlockHash())) {
        throw new BlockProcessingException(
            "Execution payload parent hash does not match previous execution payload header");
      }
    }

    if (!beaconStateAccessors
        .getRandaoMix(state, beaconStateAccessors.getCurrentEpoch(state))
        .equals(executionPayloadHeader.getPrevRandao())) {
      throw new BlockProcessingException("Execution payload randao does not match state randao");
    }

    if (!miscHelpersBellatrix
        .computeTimeAtSlot(state.getGenesisTime(), state.getSlot())
        .equals(executionPayloadHeader.getTimestamp())) {
      throw new BlockProcessingException(
          "Execution payload timestamp does not match time for state slot");
    }
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
    return new NewPayloadRequest(executionPayload);
  }

  @Override
  public boolean isOptimistic() {
    return true;
  }

  @Override
  public void processBlsToExecutionChanges(
      final MutableBeaconState state,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No BlsToExecutionChanges in Bellatrix.");
  }
}
