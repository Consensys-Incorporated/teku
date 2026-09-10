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

package tech.pegasys.teku.reference.common.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsSchemaGloas;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;
import tech.pegasys.teku.statetransition.attestation.PooledAttestationWithData;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.statetransition.validation.signatures.SimpleSignatureVerificationService;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;

public class OperationsTestExecutor<T extends SszData> implements TestExecutor {

  public static final String EXPECTED_STATE_FILE = "post.ssz_snappy";

  private enum Operation {
    ATTESTER_SLASHING,
    PROPOSER_SLASHING,
    PROCESS_BLOCK_HEADER,
    DEPOSIT,
    VOLUNTARY_EXIT,
    ATTESTATION,
    SYNC_AGGREGATE,
    EXECUTION_PAYLOAD,
    BLS_TO_EXECUTION_CHANGE,
    WITHDRAWAL,
    DEPOSIT_REQUEST,
    WITHDRAWAL_REQUEST,
    CONSOLIDATION_REQUEST,
    PARENT_EXECUTION_PAYLOAD,
    EXECUTION_PAYLOAD_BID,
    PAYLOAD_ATTESTATION,
    BUILDER_DEPOSIT_REQUEST,
    BUILDER_EXIT_REQUEST;
  }

  public static final ImmutableMap<String, TestExecutor> OPERATIONS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "operations/attester_slashing",
              new OperationsTestExecutor<>(
                  "attester_slashing.ssz_snappy", Operation.ATTESTER_SLASHING))
          .put(
              "operations/proposer_slashing",
              new OperationsTestExecutor<>(
                  "proposer_slashing.ssz_snappy", Operation.PROPOSER_SLASHING))
          .put(
              "operations/block_header",
              new OperationsTestExecutor<>("block.ssz_snappy", Operation.PROCESS_BLOCK_HEADER))
          .put(
              "operations/deposit",
              new OperationsTestExecutor<>("deposit.ssz_snappy", Operation.DEPOSIT))
          .put(
              "operations/voluntary_exit",
              new OperationsTestExecutor<>("voluntary_exit.ssz_snappy", Operation.VOLUNTARY_EXIT))
          .put(
              "operations/voluntary_exit_churn",
              new OperationsTestExecutor<>("voluntary_exit.ssz_snappy", Operation.VOLUNTARY_EXIT))
          .put(
              "operations/attestation",
              new OperationsTestExecutor<>("attestation.ssz_snappy", Operation.ATTESTATION))
          .put(
              "operations/sync_aggregate",
              new OperationsTestExecutor<>("sync_aggregate.ssz_snappy", Operation.SYNC_AGGREGATE))
          .put(
              "operations/sync_aggregate_random",
              new OperationsTestExecutor<>("sync_aggregate.ssz_snappy", Operation.SYNC_AGGREGATE))
          .put(
              "operations/execution_payload",
              new OperationsTestExecutor<>("body.ssz_snappy", Operation.EXECUTION_PAYLOAD))
          .put(
              "operations/bls_to_execution_change",
              new OperationsTestExecutor<>(
                  "address_change.ssz_snappy", Operation.BLS_TO_EXECUTION_CHANGE))
          .put(
              "operations/withdrawals",
              new OperationsTestExecutor<>("execution_payload.ssz_snappy", Operation.WITHDRAWAL))
          .put(
              "operations/deposit_request",
              new OperationsTestExecutor<>("deposit_request.ssz_snappy", Operation.DEPOSIT_REQUEST))
          .put(
              "operations/withdrawal_request",
              new OperationsTestExecutor<>(
                  "withdrawal_request.ssz_snappy", Operation.WITHDRAWAL_REQUEST))
          .put(
              "operations/consolidation_request",
              new OperationsTestExecutor<>(
                  "consolidation_request.ssz_snappy", Operation.CONSOLIDATION_REQUEST))
          .put(
              "operations/parent_execution_payload",
              new OperationsTestExecutor<>("block.ssz_snappy", Operation.PARENT_EXECUTION_PAYLOAD))
          .put(
              "operations/execution_payload_bid",
              new OperationsTestExecutor<>(
                  "execution_payload_bid.ssz_snappy", Operation.EXECUTION_PAYLOAD_BID))
          .put(
              "operations/payload_attestation",
              new OperationsTestExecutor<>(
                  "payload_attestation.ssz_snappy", Operation.PAYLOAD_ATTESTATION))
          .put(
              "operations/builder_deposit_request",
              new OperationsTestExecutor<>(
                  "builder_deposit_request.ssz_snappy", Operation.BUILDER_DEPOSIT_REQUEST))
          .put(
              "operations/builder_exit_request",
              new OperationsTestExecutor<>(
                  "builder_exit_request.ssz_snappy", Operation.BUILDER_EXIT_REQUEST))
          .build();

  private final String dataFileName;
  private final Operation operation;

  public OperationsTestExecutor(final String dataFileName, final Operation operation) {
    this.dataFileName = dataFileName;
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");

    final DefaultOperationProcessor standardProcessor =
        new DefaultOperationProcessor(testDefinition.getSpec());
    runProcessor(standardProcessor, testDefinition, preState);
  }

  private void runProcessor(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState)
      throws Exception {
    final boolean isOperationValid =
        testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILE).toFile().exists();
    if (isOperationValid) {
      assertOperationSuccessful(processor, testDefinition, preState);
    } else {
      assertOperationInvalid(testDefinition, processor, preState);
    }

    checkBlockInclusionValidation(testDefinition, preState, isOperationValid);
  }

  private void assertOperationSuccessful(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState)
      throws Exception {
    initProgressiveBalances(testDefinition, preState);

    final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILE);
    final BeaconState result = applyOperation(testDefinition, processor, preState);
    assertThat(result).isEqualTo(expectedState);

    verifyProgressiveBalances(testDefinition, result);
  }

  private void initProgressiveBalances(
      final TestDefinition testDefinition, final BeaconState state) {
    final Spec spec = testDefinition.getSpec();
    spec.atSlot(state.getSlot())
        .getEpochProcessor()
        .initProgressiveTotalBalancesIfRequired(state, calculateTotalBalances(spec, state));
  }

  /**
   * Checks the progressive balances match the non-progressive results both for the immediate state,
   * and after it goes through another epoch transition.
   */
  private void verifyProgressiveBalances(
      final TestDefinition testDefinition, final BeaconState result)
      throws SlotProcessingException, EpochProcessingException {
    final Spec spec = testDefinition.getSpec();
    if (shouldSkipProgressiveBalancesVerification(spec, result)) {
      // Skip verification if progressive balances are not used
      return;
    }

    assertTotalBalances(spec, result);
    final UInt64 nextEpoch = spec.computeEpochAtSlot(result.getSlot()).plus(1);
    final UInt64 firstSlotOfNextEpoch = spec.computeStartSlotAtEpoch(nextEpoch);
    final BeaconState nextEpochState = spec.processSlots(result, firstSlotOfNextEpoch);
    assertTotalBalances(spec, nextEpochState);
  }

  private boolean shouldSkipProgressiveBalancesVerification(
      final Spec spec, final BeaconState state) {
    if (spec.atSlot(state.getSlot()).getMilestone() == SpecMilestone.PHASE0) {
      // Progressive balances not used in Phase0
      return true;
    }

    if (spec.atSlot(state.getSlot())
        .beaconStateAccessors()
        .getActiveValidatorIndices(state, spec.getCurrentEpoch(state))
        .isEmpty()) {
      // No active validators, we can't check, epoch transition will break
      return true;
    }

    return false;
  }

  private void assertTotalBalances(final Spec spec, final BeaconState state) {
    final Optional<TotalBalances> maybeProgressiveBalances =
        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .getTotalBalances(spec.getSpecConfig(state.getSlot()));
    assertThat(maybeProgressiveBalances).isPresent();
    final TotalBalances progressiveBalances = maybeProgressiveBalances.get();

    final TotalBalances totalBalances = calculateTotalBalances(spec, state);
    assertThat(progressiveBalances).isEqualTo(totalBalances);
  }

  private TotalBalances calculateTotalBalances(final Spec spec, final BeaconState nextEpochState) {
    return spec.atSlot(nextEpochState.getSlot())
        .getValidatorStatusFactory()
        .createValidatorStatuses(nextEpochState)
        .getTotalBalances();
  }

  private void assertOperationInvalid(
      final TestDefinition testDefinition,
      final OperationProcessor processor,
      final BeaconState preState) {
    assertThatThrownBy(() -> applyOperation(testDefinition, processor, preState))
        .isInstanceOf(BlockProcessingException.class);
  }

  private BeaconState applyOperation(
      final TestDefinition testDefinition,
      final OperationProcessor processor,
      final BeaconState preState)
      throws Exception {
    return preState.updated(state -> processOperation(testDefinition, state, processor));
  }

  private void processOperation(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws Exception {
    switch (operation) {
      case ATTESTER_SLASHING -> {
        final AttesterSlashing attesterSlashing = loadAttesterSlashing(testDefinition);
        processor.processAttesterSlashing(state, attesterSlashing);
      }
      case PROPOSER_SLASHING -> {
        final ProposerSlashing proposerSlashing = loadProposerSlashing(testDefinition);
        processor.processProposerSlashing(state, proposerSlashing);
      }
      case PROCESS_BLOCK_HEADER -> {
        final BeaconBlockSummary blockHeader =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconBlockSchema());
        processor.processBlockHeader(state, blockHeader);
      }
      case DEPOSIT -> {
        final Deposit deposit = loadSsz(testDefinition, dataFileName, Deposit.SSZ_SCHEMA);
        processor.processDeposit(state, deposit);
      }
      case VOLUNTARY_EXIT -> {
        final SignedVoluntaryExit voluntaryExit = loadVoluntaryExit(testDefinition);
        processor.processVoluntaryExit(state, voluntaryExit);
      }
      case ATTESTATION -> {
        final Attestation attestation =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition.getSpec().getGenesisSchemaDefinitions().getAttestationSchema());
        final BeaconState preState = state.commitChanges();
        processor.processAttestation(state, attestation);

        verifyRewardBasedAttestationSorter(testDefinition.getSpec(), preState, state, attestation);
      }
      case SYNC_AGGREGATE -> {
        final SyncAggregate syncAggregate =
            loadSsz(
                testDefinition,
                dataFileName,
                BeaconBlockBodySchemaAltair.required(
                        testDefinition
                            .getSpec()
                            .getGenesisSchemaDefinitions()
                            .getBeaconBlockBodySchema())
                    .getSyncAggregateSchema());
        processor.processSyncCommittee(state, syncAggregate);
      }
      case EXECUTION_PAYLOAD -> {
        final ExecutionMeta executionMeta =
            loadYaml(testDefinition, "execution.yaml", ExecutionMeta.class);

        final SchemaDefinitions schemaDefinitions =
            testDefinition.getSpec().getGenesisSchemaDefinitions();
        final BeaconBlockBody beaconBlockBody =
            loadSsz(testDefinition, dataFileName, schemaDefinitions.getBeaconBlockBodySchema());
        processor.processExecutionPayload(
            state,
            beaconBlockBody,
            Optional.of(
                (latestExecutionPayloadHeader, payloadToExecute) -> executionMeta.executionValid));
      }
      case BLS_TO_EXECUTION_CHANGE -> processBlsToExecutionChange(testDefinition, state, processor);
      case WITHDRAWAL -> processWithdrawal(testDefinition, state, processor);
      case DEPOSIT_REQUEST -> processDepositRequest(testDefinition, state, processor);
      case WITHDRAWAL_REQUEST -> processWithdrawalRequest(testDefinition, state, processor);
      case CONSOLIDATION_REQUEST -> processConsolidations(testDefinition, state, processor);
      case PARENT_EXECUTION_PAYLOAD -> {
        final BeaconBlock beaconBlock =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconBlockSchema());
        processor.processParentExecutionPayload(state, beaconBlock);
      }
      case EXECUTION_PAYLOAD_BID -> {
        final SignedExecutionPayloadBid signedBid =
            loadSsz(
                testDefinition,
                dataFileName,
                SchemaDefinitionsGloas.required(
                        testDefinition.getSpec().getGenesisSchemaDefinitions())
                    .getSignedExecutionPayloadBidSchema());
        processor.processExecutionPayloadBid(state, signedBid);
      }
      case PAYLOAD_ATTESTATION -> {
        final PayloadAttestation payloadAttestation =
            loadSsz(
                testDefinition,
                dataFileName,
                SchemaDefinitionsGloas.required(
                        testDefinition.getSpec().getGenesisSchemaDefinitions())
                    .getPayloadAttestationSchema());
        processor.processPayloadAttestation(state, payloadAttestation);
      }
      case BUILDER_DEPOSIT_REQUEST ->
          processBuilderDepositRequest(testDefinition, state, processor);
      case BUILDER_EXIT_REQUEST -> processBuilderExitRequest(testDefinition, state, processor);
      default ->
          throw new UnsupportedOperationException(
              "Operation " + operation + " not implemented in OperationTestExecutor");
    }
  }

  private void processWithdrawal(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws BlockProcessingException {
    final SchemaDefinitionsCapella schemaDefinitionsCapella =
        SchemaDefinitionsCapella.required(testDefinition.getSpec().getGenesisSchemaDefinitions());
    final Optional<ExecutionPayloadSummary> executionPayload;
    if (schemaDefinitionsCapella.toVersionGloas().isPresent()) {
      // no execution payload in withdrawals tests for >= Gloas
      executionPayload = Optional.empty();
    } else {
      executionPayload =
          Optional.of(
              loadSsz(
                  testDefinition,
                  dataFileName,
                  schemaDefinitionsCapella.getExecutionPayloadSchema()));
    }
    processor.processWithdrawals(state, executionPayload);
  }

  private void processBlsToExecutionChange(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws BlockProcessingException {
    final SignedBlsToExecutionChange blsToExecutionChange =
        loadBlsToExecutionChange(testDefinition);
    processor.processBlsToExecutionChange(state, blsToExecutionChange);
  }

  private void processDepositRequest(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor) {
    final SszListSchema<DepositRequest, ?> depositRequestsSchema =
        SchemaDefinitionsElectra.required(testDefinition.getSpec().getGenesisSchemaDefinitions())
            .getExecutionRequestsSchema()
            .getDepositRequestsSchema();
    final SszList<DepositRequest> depositRequests =
        loadSsz(testDefinition, dataFileName, depositRequestsSchema);

    processor.processDepositRequest(state, depositRequests.asList());
  }

  private void processWithdrawalRequest(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor) {
    final SszListSchema<WithdrawalRequest, ?> withdrawalRequestsSchema =
        SchemaDefinitionsElectra.required(testDefinition.getSpec().getGenesisSchemaDefinitions())
            .getExecutionRequestsSchema()
            .getWithdrawalRequestsSchema();
    final SszList<WithdrawalRequest> withdrawalRequests =
        loadSsz(testDefinition, dataFileName, withdrawalRequestsSchema);

    processor.processWithdrawalRequest(state, withdrawalRequests.asList());
  }

  private void processConsolidations(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor) {
    final SszListSchema<ConsolidationRequest, ?> consolidationRequestsSchema =
        SchemaDefinitionsElectra.required(testDefinition.getSpec().getGenesisSchemaDefinitions())
            .getExecutionRequestsSchema()
            .getConsolidationRequestsSchema();
    final SszList<ConsolidationRequest> consolidationRequests =
        loadSsz(testDefinition, dataFileName, consolidationRequestsSchema);

    processor.processConsolidationRequests(state, consolidationRequests.asList());
  }

  private void processBuilderDepositRequest(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor) {
    final SszListSchema<BuilderDepositRequest, ?> builderDepositRequestsSchema =
        ExecutionRequestsSchemaGloas.required(
                SchemaDefinitionsGloas.required(
                        testDefinition.getSpec().getGenesisSchemaDefinitions())
                    .getExecutionRequestsSchema())
            .getBuilderDepositRequestsSchema();
    final SszList<BuilderDepositRequest> builderDepositRequests =
        loadSsz(testDefinition, dataFileName, builderDepositRequestsSchema);
    processor.processBuilderDepositRequest(state, builderDepositRequests.asList());
  }

  private void processBuilderExitRequest(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor) {
    final SszListSchema<BuilderExitRequest, ?> builderExitRequestsSchema =
        ExecutionRequestsSchemaGloas.required(
                SchemaDefinitionsGloas.required(
                        testDefinition.getSpec().getGenesisSchemaDefinitions())
                    .getExecutionRequestsSchema())
            .getBuilderExitRequestsSchema();
    final SszList<BuilderExitRequest> builderExitRequests =
        loadSsz(testDefinition, dataFileName, builderExitRequestsSchema);
    processor.processBuilderExitRequest(state, builderExitRequests.asList());
  }

  private SignedVoluntaryExit loadVoluntaryExit(final TestDefinition testDefinition) {
    return loadSsz(testDefinition, dataFileName, SignedVoluntaryExit.SSZ_SCHEMA);
  }

  private ProposerSlashing loadProposerSlashing(final TestDefinition testDefinition) {
    return loadSsz(testDefinition, dataFileName, ProposerSlashing.SSZ_SCHEMA);
  }

  private AttesterSlashing loadAttesterSlashing(final TestDefinition testDefinition) {
    return loadSsz(
        testDefinition,
        dataFileName,
        testDefinition.getSpec().getGenesisSchemaDefinitions().getAttesterSlashingSchema());
  }

  private SignedBlsToExecutionChange loadBlsToExecutionChange(final TestDefinition testDefinition) {
    final SchemaDefinitionsCapella schemaDefinitionsCapella =
        SchemaDefinitionsCapella.required(testDefinition.getSpec().getGenesisSchemaDefinitions());
    return loadSsz(
        testDefinition,
        dataFileName,
        schemaDefinitionsCapella.getSignedBlsToExecutionChangeSchema());
  }

  public void checkBlockInclusionValidation(
      final TestDefinition testDefinition, final BeaconState state, final boolean expectInclusion) {
    final Spec spec = testDefinition.getSpec();
    final TimeProvider timeProvider = new SystemTimeProvider();
    switch (operation) {
      case ATTESTER_SLASHING -> {
        final AttesterSlashing attesterSlashing = loadAttesterSlashing(testDefinition);
        final AttesterSlashingValidator validator = new AttesterSlashingValidator(null, spec);
        checkValidationForBlockInclusion(validator, state, attesterSlashing, expectInclusion);
      }
      case PROPOSER_SLASHING -> {
        final ProposerSlashing proposerSlashing = loadProposerSlashing(testDefinition);
        final ProposerSlashingValidator proposerValidator =
            new ProposerSlashingValidator(spec, null);
        checkValidationForBlockInclusion(
            proposerValidator, state, proposerSlashing, expectInclusion);
      }
      case VOLUNTARY_EXIT -> {
        final SignedVoluntaryExit voluntaryExit = loadVoluntaryExit(testDefinition);
        final VoluntaryExitValidator voluntaryExitValidator =
            new VoluntaryExitValidator(
                spec,
                null,
                timeProvider,
                new GossipValidationHelper(
                    spec, MemoryOnlyRecentChainData.create(spec), new StubMetricsSystem()));
        checkValidationForBlockInclusion(
            voluntaryExitValidator, state, voluntaryExit, expectInclusion);
      }
      case BLS_TO_EXECUTION_CHANGE -> {
        final SignedBlsToExecutionChangeValidator blsToExecutionChangeValidator =
            new SignedBlsToExecutionChangeValidator(
                spec, timeProvider, null, new SimpleSignatureVerificationService());
        final SignedBlsToExecutionChange blsToExecutionChange =
            loadBlsToExecutionChange(testDefinition);
        checkValidationForBlockInclusion(
            blsToExecutionChangeValidator, state, blsToExecutionChange, expectInclusion);
      }
      // Not yet testing inclusion rules
      case PROCESS_BLOCK_HEADER,
          DEPOSIT,
          ATTESTATION,
          SYNC_AGGREGATE,
          EXECUTION_PAYLOAD,
          WITHDRAWAL,
          DEPOSIT_REQUEST,
          WITHDRAWAL_REQUEST,
          CONSOLIDATION_REQUEST,
          PARENT_EXECUTION_PAYLOAD,
          EXECUTION_PAYLOAD_BID,
          PAYLOAD_ATTESTATION,
          BUILDER_DEPOSIT_REQUEST,
          BUILDER_EXIT_REQUEST -> {}
    }
  }

  private <O> void checkValidationForBlockInclusion(
      final OperationValidator<O> validator,
      final BeaconState state,
      final O operation,
      final boolean expectInclusion) {
    final Optional<String> invalidReason =
        validator.validateForBlockInclusion(state, operation).map(OperationInvalidReason::describe);
    if (expectInclusion) {
      assertThat(invalidReason).isEmpty();
    } else {
      assertThat(invalidReason).isPresent();
    }
  }

  private static class ExecutionMeta {
    @JsonProperty(value = "execution_valid", required = true)
    private boolean executionValid;
  }

  private static final long PROPOSER_REWARD_DENOMINATOR =
      WEIGHT_DENOMINATOR
          .minus(PROPOSER_WEIGHT)
          .times(WEIGHT_DENOMINATOR)
          .dividedBy(PROPOSER_WEIGHT)
          .longValue();

  /** checks that our sorter */
  private void verifyRewardBasedAttestationSorter(
      final Spec spec,
      final BeaconState preState,
      final BeaconState postState,
      final Attestation attestation) {
    var specVersion = spec.atSlot(preState.getSlot());

    var blockProcessorAltair = specVersion.getBlockProcessor().toVersionAltair();
    if (blockProcessorAltair.isEmpty()) {
      // Phase0 is not supported
      return;
    }

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestation);
    validatableAttestation.saveCommitteesSize(preState);

    var attestationIndices =
        specVersion.getAttestationUtil().getAttestingIndices(preState, attestation);
    var pooledAttestation =
        new PooledAttestationWithData(
            attestation.getData(),
            PooledAttestation.fromValidatableAttestation(
                validatableAttestation, attestationIndices));

    final List<PooledAttestationWithRewardInfo> sortedPooledAttestations =
        RewardBasedAttestationSorter.createForReferenceTest(spec, preState)
            .sort(List.of(pooledAttestation), 1);

    final UInt64 reward =
        sortedPooledAttestations
            .getFirst()
            .getRewardNumerator()
            .dividedBy(PROPOSER_REWARD_DENOMINATOR);

    var proposerIndex = specVersion.beaconStateAccessors().getBeaconProposerIndex(preState);
    var preBalance = preState.getBalances().get(proposerIndex).get();
    var postBalance = postState.getBalances().get(proposerIndex).get();

    final UInt64 expectedReward = postBalance.minus(preBalance);

    assertThat(reward).isEqualTo(expectedReward);
  }
}
