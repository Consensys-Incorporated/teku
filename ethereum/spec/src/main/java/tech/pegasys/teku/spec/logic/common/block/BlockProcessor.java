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

package tech.pegasys.teku.spec.logic.common.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface BlockProcessor {

  Optional<OperationInvalidReason> validateAttestation(BeaconState state, AttestationData data);

  BeaconState processAndValidateBlock(
      SignedBeaconBlock signedBlock,
      BeaconState blockSlotState,
      IndexedAttestationCache indexedAttestationCache,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException;

  /**
   * Processes the given block on top of {@code blockSlotState} and optionally validates the block
   *
   * @param signedBlock The block to be processed
   * @param blockSlotState The preState on which this block should be processed, this preState must
   *     already be advanced to the block's slot
   * @param indexedAttestationCache A cache of indexed attestations
   * @param signatureVerifier The signature verifier to use
   * @param payloadExecutor the optimistic payload executor to begin execution with
   * @return The post state after processing the block on top of {@code blockSlotState}
   * @throws StateTransitionException If the block is invalid or cannot be processed
   */
  BeaconState processAndValidateBlock(
      SignedBeaconBlock signedBlock,
      BeaconState blockSlotState,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException;

  BeaconState processUnsignedBlock(
      BeaconState preState,
      BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException;

  boolean isEnoughVotesToUpdateEth1Data(long voteCount);

  long getVoteCount(BeaconState state, Eth1Data eth1Data);

  void processProposerSlashings(
      MutableBeaconState state,
      SszList<ProposerSlashing> proposerSlashings,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException;

  void processAttesterSlashings(
      MutableBeaconState state,
      SszList<AttesterSlashing> attesterSlashings,
      Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException;

  void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processDeposits(MutableBeaconState state, SszList<Deposit> deposits)
      throws BlockProcessingException;

  void processDepositWithoutCheckingMerkleProof(
      MutableBeaconState state,
      Deposit deposit,
      Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      boolean signatureAlreadyVerified);

  void processVoluntaryExits(
      MutableBeaconState state,
      SszList<SignedVoluntaryExit> exits,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processSyncAggregate(
      MutableBeaconState state, SyncAggregate syncAggregate, BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  UInt64 computeParticipantReward(BeaconStateAltair state);

  void processExecutionPayload(
      MutableBeaconState state,
      BeaconBlockBody beaconBlockBody,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  void validateExecutionPayload(
      BeaconState state,
      BeaconBlockBody beaconBlockBody,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  NewPayloadRequest computeNewPayloadRequest(BeaconState state, BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException;

  void validateExecutionPayloadHeader(
      BeaconState state, ExecutionPayloadHeader executionPayloadHeader)
      throws BlockProcessingException;

  boolean isOptimistic();

  void processBlsToExecutionChanges(
      MutableBeaconState state, SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException;

  void processParentExecutionPayload(
      MutableBeaconState state,
      BeaconBlock beaconBlock,
      Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException;

  void processWithdrawals(
      MutableBeaconState state, Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException;

  void processExecutionPayloadBid(MutableBeaconState state, SignedExecutionPayloadBid signedBid)
      throws BlockProcessingException;

  void processPayloadAttestations(
      MutableBeaconState state, SszList<PayloadAttestation> payloadAttestations)
      throws BlockProcessingException;

  default Optional<BlockProcessorAltair> toVersionAltair() {
    return Optional.empty();
  }
}
