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

package tech.pegasys.teku.reference.common.operations;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
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
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface OperationProcessor {

  void processAttesterSlashing(MutableBeaconState state, AttesterSlashing attesterSlashings)
      throws BlockProcessingException;

  void processProposerSlashing(MutableBeaconState state, ProposerSlashing proposerSlashing)
      throws BlockProcessingException;

  void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException;

  void processDeposit(MutableBeaconState state, Deposit deposit) throws BlockProcessingException;

  void processVoluntaryExit(MutableBeaconState state, SignedVoluntaryExit voluntaryExit)
      throws BlockProcessingException;

  void processAttestation(MutableBeaconState state, Attestation attestation)
      throws BlockProcessingException;

  void processSyncCommittee(MutableBeaconState state, SyncAggregate aggregate)
      throws BlockProcessingException;

  void processExecutionPayload(
      MutableBeaconState state,
      BeaconBlockBody beaconBlockBody,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  void processBlsToExecutionChange(
      MutableBeaconState state, SignedBlsToExecutionChange blsToExecutionChange)
      throws BlockProcessingException;

  void processWithdrawals(
      MutableBeaconState state, Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException;

  void processDepositRequest(MutableBeaconState state, List<DepositRequest> depositRequest);

  void processWithdrawalRequest(
      MutableBeaconState state, List<WithdrawalRequest> withdrawalRequest);

  void processConsolidationRequests(
      MutableBeaconState state, List<ConsolidationRequest> consolidationRequest);

  void processParentExecutionPayload(MutableBeaconState state, BeaconBlock beaconBlock)
      throws BlockProcessingException;

  void processExecutionPayloadBid(
      MutableBeaconState state, SignedExecutionPayloadBid executionPayloadBid)
      throws BlockProcessingException;

  void processPayloadAttestation(MutableBeaconState state, PayloadAttestation payloadAttestation)
      throws BlockProcessingException;

  void processBuilderDepositRequest(
      MutableBeaconState state, List<BuilderDepositRequest> depositRequest);

  void processBuilderExitRequest(MutableBeaconState state, List<BuilderExitRequest> depositRequest);
}
