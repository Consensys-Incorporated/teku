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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator.AttestationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.withdrawals.WithdrawalsHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorElectra extends BlockProcessorDeneb {

  private final BeaconStateAccessorsElectra beaconStateAccessorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;
  private final ExecutionRequestsDataCodec executionRequestsDataCodec;
  protected final ExecutionRequestsProcessor executionRequestsProcessor;

  public BlockProcessorElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersElectra miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions,
      final WithdrawalsHelpersElectra withdrawalsHelpers,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor) {
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
        schemaDefinitions,
        withdrawalsHelpers);
    this.beaconStateAccessorsElectra = beaconStateAccessors;
    this.schemaDefinitionsElectra = schemaDefinitions;
    this.executionRequestsDataCodec = executionRequestsDataCodec;
    this.executionRequestsProcessor = executionRequestsProcessor;
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
    final ExecutionRequests executionRequests =
        beaconBlockBody
            .getOptionalExecutionRequests()
            .orElseThrow(() -> new BlockProcessingException("Execution requests expected"));
    return new NewPayloadRequest(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec.encode(executionRequests));
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier,
      final UInt64 parentSlot)
      throws BlockProcessingException {
    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier, parentSlot);

    safelyProcess(() -> processExecutionRequests(state, body, validatorExitContextSupplier));
  }

  public void processExecutionRequests(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    final ExecutionRequests executionRequests =
        body.getOptionalExecutionRequests()
            .orElseThrow(
                () -> new BlockProcessingException("Execution requests expected as part of body"));

    executionRequestsProcessor.processDepositRequests(state, executionRequests.getDeposits());
    executionRequestsProcessor.processWithdrawalRequests(
        state, executionRequests.getWithdrawals(), validatorExitContextSupplier);
    executionRequestsProcessor.processConsolidationRequests(
        state, executionRequests.getConsolidations());
  }

  @Override
  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final UInt64 eth1DepositIndexLimit =
        state
            .getEth1Data()
            .getDepositCount()
            .min(BeaconStateElectra.required(state).getDepositRequestsStartIndex());

    if (state.getEth1DepositIndex().isLessThan(eth1DepositIndexLimit)) {
      final int expectedDepositCount =
          Math.min(
              specConfig.getMaxDeposits(),
              eth1DepositIndexLimit.minusMinZero(state.getEth1DepositIndex()).intValue());

      checkArgument(
          body.getDeposits().size() == expectedDepositCount,
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
    } else {
      checkArgument(
          body.getDeposits().isEmpty(),
          "process_operations: Verify that former deposit mechanism has been disabled");
    }
  }

  @Override
  public void applyDeposit(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {

    // Find the validator index associated with this deposit, if it exists
    final Optional<Integer> existingIndex =
        maybePubkeyToIndexMap
            .flatMap(
                pubkeyToIndexMap -> {
                  if (pubkeyToIndexMap.containsKey(pubkey)) {
                    return Optional.of(pubkeyToIndexMap.getInt(pubkey));
                  } else {
                    pubkeyToIndexMap.put(pubkey, state.getValidators().size());
                    return Optional.empty();
                  }
                })
            .or(() -> validatorsUtil.getValidatorIndex(state, pubkey));

    if (existingIndex.isEmpty()) {
      // This is a new validator
      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (signatureAlreadyVerified
          || miscHelpers.isValidDepositSignature(
              pubkey, withdrawalCredentials, amount, signature)) {
        beaconStateMutators.addValidatorToRegistry(state, pubkey, withdrawalCredentials, ZERO);
        final PendingDeposit deposit =
            schemaDefinitionsElectra
                .getPendingDepositSchema()
                .create(
                    new SszPublicKey(pubkey),
                    SszBytes32.of(withdrawalCredentials),
                    SszUInt64.of(amount),
                    new SszSignature(signature),
                    SszUInt64.of(SpecConfig.GENESIS_SLOT));
        MutableBeaconStateElectra.required(state).getPendingDeposits().append(deposit);
      } else {
        handleInvalidDeposit(pubkey, maybePubkeyToIndexMap);
      }
    } else {
      final PendingDeposit deposit =
          schemaDefinitionsElectra
              .getPendingDepositSchema()
              .create(
                  new SszPublicKey(pubkey),
                  SszBytes32.of(withdrawalCredentials),
                  SszUInt64.of(amount),
                  new SszSignature(signature),
                  SszUInt64.of(SpecConfig.GENESIS_SLOT));
      MutableBeaconStateElectra.required(state).getPendingDeposits().append(deposit);
    }
  }

  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {
    state.setEth1DepositIndex(state.getEth1DepositIndex().plus(UInt64.ONE));

    applyDeposit(
        state,
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawalCredentials(),
        deposit.getData().getAmount(),
        deposit.getData().getSignature(),
        maybePubkeyToIndexMap,
        signatureAlreadyVerified);
  }

  @Override
  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final Optional<OperationInvalidReason> invalidReason =
        validateAttestation(state, attestation.getData());
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    final List<UInt64> committeeIndices = attestation.getCommitteeIndicesRequired();
    final UInt64 committeeCountPerSlot =
        beaconStateAccessorsElectra.getCommitteeCountPerSlot(
            state, attestation.getData().getTarget().getEpoch());
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final Optional<OperationInvalidReason> committeeCheckResult =
        checkCommittees(
            committeeIndices,
            committeeCountPerSlot,
            state,
            attestation.getData().getSlot(),
            aggregationBits);
    if (committeeCheckResult.isPresent()) {
      throw new IllegalArgumentException(committeeCheckResult.get().describe());
    }
  }

  private Optional<OperationInvalidReason> checkCommittees(
      final List<UInt64> committeeIndices,
      final UInt64 committeeCountPerSlot,
      final BeaconState state,
      final UInt64 slot,
      final SszBitlist aggregationBits) {
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      if (committeeIndex.isGreaterThanOrEqualTo(committeeCountPerSlot)) {
        return Optional.of(AttestationInvalidReason.COMMITTEE_INDEX_TOO_HIGH);
      }
      final IntList committee =
          beaconStateAccessorsElectra.getBeaconCommittee(state, slot, committeeIndex);
      final int currentCommitteeOffset = committeeOffset;
      final boolean committeeHasAtLeastOneAttester =
          IntStream.range(0, committee.size())
              .anyMatch(
                  committeeParticipantIndex ->
                      aggregationBits.isSet(currentCommitteeOffset + committeeParticipantIndex));
      if (!committeeHasAtLeastOneAttester) {
        return Optional.of(AttestationInvalidReason.PARTICIPANTS_COUNT_MISMATCH);
      }
      committeeOffset += committee.size();
    }
    if (committeeOffset != aggregationBits.size()) {
      return Optional.of(AttestationInvalidReason.PARTICIPANTS_COUNT_MISMATCH);
    }
    return Optional.empty();
  }
}
