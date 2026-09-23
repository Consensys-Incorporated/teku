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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public class DefaultOperationProcessor implements OperationProcessor {

  private final Spec spec;
  private final BeaconBlockBodySchema<?> beaconBlockBodySchema;

  public DefaultOperationProcessor(final TestDefinition testDefinition) {
    this.spec = testDefinition.getSpec();
    this.beaconBlockBodySchema =
        spec.forMilestone(testDefinition.getMilestone())
            .getSchemaDefinitions()
            .getBeaconBlockBodySchema();
  }

  @Override
  public void processAttesterSlashing(
      final MutableBeaconState state, final AttesterSlashing attesterSlashings)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processAttesterSlashings(
            state, beaconBlockBodySchema.getAttesterSlashingsSchema().of(attesterSlashings));
  }

  @Override
  public void processProposerSlashing(
      final MutableBeaconState state, final ProposerSlashing proposerSlashing)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processProposerSlashings(
            state,
            beaconBlockBodySchema.getProposerSlashingsSchema().of(proposerSlashing),
            BLSSignatureVerifier.SIMPLE);
  }

  @Override
  public void processBlockHeader(
      final MutableBeaconState state, final BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot()).processBlockHeader(state, blockHeader);
  }

  @Override
  public void processDeposit(final MutableBeaconState state, final Deposit deposit)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processDeposits(state, beaconBlockBodySchema.getDepositsSchema().of(deposit));
  }

  @Override
  public void processVoluntaryExit(
      final MutableBeaconState state, final SignedVoluntaryExit voluntaryExit)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processVoluntaryExits(
            state,
            beaconBlockBodySchema.getVoluntaryExitsSchema().of(voluntaryExit),
            BLSSignatureVerifier.SIMPLE);
  }

  @Override
  public void processAttestation(final MutableBeaconState state, final Attestation attestation)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processAttestations(
            state,
            beaconBlockBodySchema.getAttestationsSchema().of(attestation),
            BLSSignatureVerifier.SIMPLE);
  }

  @Override
  public void processSyncCommittee(final MutableBeaconState state, final SyncAggregate aggregate)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processSyncAggregate(state, aggregate, BLSSignatureVerifier.SIMPLE);
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processExecutionPayload(state, beaconBlockBody, payloadExecutor);
  }

  @Override
  public void processBlsToExecutionChange(
      final MutableBeaconState state, final SignedBlsToExecutionChange blsToExecutionChange)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processBlsToExecutionChanges(
            state,
            BeaconBlockBodySchemaCapella.required(beaconBlockBodySchema)
                .getBlsToExecutionChangesSchema()
                .of(blsToExecutionChange));
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot()).processWithdrawals(state, payloadSummary);
  }

  @Override
  public void processDepositRequest(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    spec.getExecutionRequestsProcessor(state.getSlot())
        .processDepositRequests(state, depositRequests);
  }

  @Override
  public void processWithdrawalRequest(
      final MutableBeaconState state, final List<WithdrawalRequest> withdrawalRequests) {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        spec.atSlot(state.getSlot())
            .beaconStateMutators()
            .createValidatorExitContextSupplier(state);
    spec.getExecutionRequestsProcessor(state.getSlot())
        .processWithdrawalRequests(state, withdrawalRequests, validatorExitContextSupplier);
  }

  @Override
  public void processConsolidationRequests(
      final MutableBeaconState state, final List<ConsolidationRequest> consolidationRequests) {
    spec.getExecutionRequestsProcessor(state.getSlot())
        .processConsolidationRequests(state, consolidationRequests);
  }

  @Override
  public void processParentExecutionPayload(
      final MutableBeaconState state, final BeaconBlock beaconBlock)
      throws BlockProcessingException {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        spec.atSlot(state.getSlot())
            .beaconStateMutators()
            .createValidatorExitContextSupplier(state);
    spec.getBlockProcessor(state.getSlot())
        .processParentExecutionPayload(state, beaconBlock, validatorExitContextSupplier);
  }

  @Override
  public void processExecutionPayloadBid(
      final MutableBeaconState state, final SignedExecutionPayloadBid signedBid)
      throws BlockProcessingException {
    spec.getBlockProcessor(signedBid.getMessage().getSlot())
        .processExecutionPayloadBid(state, signedBid);
  }

  @Override
  public void processPayloadAttestation(
      final MutableBeaconState state, final PayloadAttestation payloadAttestation)
      throws BlockProcessingException {
    spec.getBlockProcessor(state.getSlot())
        .processPayloadAttestations(
            state,
            BeaconBlockBodySchemaGloas.required(beaconBlockBodySchema)
                .getPayloadAttestationsSchema()
                .of(payloadAttestation));
  }

  @Override
  public void processBuilderDepositRequest(
      final MutableBeaconState state, final List<BuilderDepositRequest> builderDepositRequests) {
    spec.getExecutionRequestsProcessor(state.getSlot())
        .processBuilderDepositRequests(state, builderDepositRequests);
  }

  @Override
  public void processBuilderExitRequest(
      final MutableBeaconState state, final List<BuilderExitRequest> builderExitRequests) {
    spec.getExecutionRequestsProcessor(state.getSlot())
        .processBuilderExitRequests(state, builderExitRequests);
  }
}
