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

package tech.pegasys.teku.spec.logic.versions.phase0.block;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public final class BlockProcessorPhase0 extends AbstractBlockProcessor {

  public BlockProcessorPhase0(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator);
  }

  @Override
  protected void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider,
      final UInt64 parentSlot) {
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);
    final AttestationData data = attestation.getData();

    PendingAttestation pendingAttestation =
        state
            .getBeaconStateSchema()
            .getPendingAttestationSchema()
            .create(
                attestation.getAggregationBits(),
                data,
                state.getSlot().minus(data.getSlot()),
                UInt64.valueOf(beaconStateAccessors.getBeaconProposerIndex(state)));

    if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
      state.getCurrentEpochAttestations().append(pendingAttestation);
    } else {
      state.getPreviousEpochAttestations().append(pendingAttestation);
    }
  }

  @Override
  public void processSyncAggregate(
      final MutableBeaconState state,
      final SyncAggregate syncAggregate,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No SyncAggregates in phase0");
  }

  @Override
  public UInt64 computeParticipantReward(final BeaconStateAltair state) {
    return UInt64.ZERO;
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayload in phase0");
  }

  @Override
  public void validateExecutionPayload(
      final BeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayload in phase0");
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No NewPayloadRequest in phase0");
  }

  @Override
  public void validateExecutionPayloadHeader(
      final BeaconState state, final ExecutionPayloadHeader executionPayloadHeader)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No ExecutionPayloadHeader in phase0");
  }

  @Override
  public boolean isOptimistic() {
    return false;
  }

  @Override
  public void processBlsToExecutionChanges(
      final MutableBeaconState state,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
    throw new UnsupportedOperationException("No BlsToExecutionChanges in phase0");
  }

  @Override
  public void processParentExecutionPayload(
      final MutableBeaconState state,
      final BeaconBlock beaconBlock,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier) {
    throw new UnsupportedOperationException("No process_parent_execution_payload until Gloas");
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No withdrawals in phase0");
  }

  @Override
  public void processExecutionPayloadBid(
      final MutableBeaconState state, final SignedExecutionPayloadBid signedBid) {
    throw new UnsupportedOperationException("No process_execution_payload_bid until Gloas");
  }

  @Override
  public void processPayloadAttestations(
      final MutableBeaconState state, final SszList<PayloadAttestation> payloadAttestations) {
    throw new UnsupportedOperationException("No payload attestations until Gloas");
  }
}
