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

package tech.pegasys.teku.spec.logic.versions.capella.block;

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_PREFIX;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.BlockProcessorBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.logic.versions.capella.withdrawals.WithdrawalsHelpersCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class BlockProcessorCapella extends BlockProcessorBellatrix {
  private static final Bytes ETH1_WITHDRAWAL_KEY_PREFIX =
      Bytes.concatenate(ETH1_ADDRESS_WITHDRAWAL_PREFIX, Bytes.repeat((byte) 0x00, 11));
  protected final WithdrawalsHelpers withdrawalsHelpers;

  public BlockProcessorCapella(
      final SpecConfigCapella specConfig,
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
      final SchemaDefinitionsCapella schemaDefinitions,
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
        SchemaDefinitionsBellatrix.required(schemaDefinitions));
    this.withdrawalsHelpers = withdrawalsHelpers;
  }

  @Override
  public void executionProcessing(
      final MutableBeaconState genericState,
      final BeaconBlock beaconBlock,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final ExecutionPayloadHeader executionPayloadHeader =
        extractExecutionPayloadHeader(beaconBlock.getBody());
    processWithdrawals(genericState, Optional.of(executionPayloadHeader));
    super.executionProcessing(genericState, beaconBlock, payloadExecutor);
  }

  @Override
  @CheckReturnValue
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final SszList<Deposit> deposits = block.getMessage().getBody().getDeposits();
    BeaconState validationState = preState;
    final SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges =
        block
            .getMessage()
            .getBody()
            .getOptionalBlsToExecutionChanges()
            .orElseThrow(
                () ->
                    new BlockProcessingException(
                        "BlsToExecutionChanges was not found during block processing."));

    BlockValidationResult blockValidationResult =
        verifyBlsToExecutionChangesPreProcessing(
            validationState, signedBlsToExecutionChanges, signatureVerifier);

    if (!blockValidationResult.isValid() && !deposits.isEmpty()) {
      // It's possible that a bls change referred to a validator which is in the
      // pending deposits list, so apply deposits and attempt validation again.
      final MutableBeaconStateCapella mutableBeaconStateCapella =
          BeaconStateCapella.required(preState).createWritableCopy();
      processDeposits(mutableBeaconStateCapella, block.getMessage().getBody().getDeposits());
      validationState = mutableBeaconStateCapella.commitChanges();
      blockValidationResult =
          verifyBlsToExecutionChangesPreProcessing(
              validationState, signedBlsToExecutionChanges, signatureVerifier);
    }
    return blockValidationResult;
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier, parentSlot);

    safelyProcess(
        () ->
            processBlsToExecutionChanges(
                MutableBeaconStateCapella.required(state),
                body.getOptionalBlsToExecutionChanges()
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "BlsToExecutionChanges was not found during block processing."))));
  }

  // process_bls_to_execution_change
  @Override
  public void processBlsToExecutionChanges(
      final MutableBeaconState state,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException {
    final BlockValidationResult result =
        verifyBlsToExecutionChangesPreProcessing(
            state, blsToExecutionChanges, BLSSignatureVerifier.SIMPLE);
    if (!result.isValid()) {
      throw new BlockProcessingException(result.getFailureReason());
    }

    for (SignedBlsToExecutionChange signedBlsToExecutionChange : blsToExecutionChanges) {
      BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();
      final int validatorIndex = addressChange.getValidatorIndex().intValue();
      Validator validator = state.getValidators().get(validatorIndex);
      state
          .getValidators()
          .set(
              validatorIndex,
              validator.withWithdrawalCredentials(
                  getWithdrawalAddressFromEth1Address(addressChange.getToExecutionAddress())));
    }
  }

  // process_withdrawals
  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    safelyProcess(() -> withdrawalsHelpers.processWithdrawals(state, payloadSummary.orElseThrow()));
  }

  @VisibleForTesting
  public static Bytes32 getWithdrawalAddressFromEth1Address(final Bytes20 toExecutionAddress) {
    return Bytes32.wrap(
        Bytes.concatenate(ETH1_WITHDRAWAL_KEY_PREFIX, toExecutionAddress.getWrappedBytes()));
  }

  @VisibleForTesting
  BlockValidationResult verifyBlsToExecutionChangesPreProcessing(
      final BeaconState genericState,
      final SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges,
      final BLSSignatureVerifier signatureVerifier) {

    final Set<UInt64> validatorsSeenInBlock = new HashSet<>();
    for (SignedBlsToExecutionChange signedBlsToExecutionChange : signedBlsToExecutionChanges) {
      final BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();

      if (!validatorsSeenInBlock.add(addressChange.getValidatorIndex())) {
        return BlockValidationResult.failed(
            "Duplicated BlsToExecutionChange for validator " + addressChange.getValidatorIndex());
      }

      final Optional<OperationInvalidReason> operationInvalidReason =
          operationValidator.validateBlsToExecutionChange(
              genericState.getFork(), genericState, addressChange);
      if (operationInvalidReason.isPresent()) {
        return BlockValidationResult.failed(operationInvalidReason.get().describe());
      }

      boolean signatureValid =
          operationSignatureVerifier.verifyBlsToExecutionChangeSignature(
              genericState, signedBlsToExecutionChange, signatureVerifier);
      if (!signatureValid) {
        return BlockValidationResult.failed(
            "BlsToExecutionChange signature is invalid: " + signedBlsToExecutionChange);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }
}
