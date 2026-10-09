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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerificationException;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerifier;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

public class ExecutionPayloadVerifierGloas implements ExecutionPayloadVerifier {

  private final MiscHelpersGloas miscHelpers;
  private final BeaconStateAccessorsGloas beaconStateAccessors;
  private final ExecutionRequestsDataCodec executionRequestsDataCodec;

  public ExecutionPayloadVerifierGloas(
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.executionRequestsDataCodec = executionRequestsDataCodec;
  }

  /** Accepts the block's post-state or a checkpoint state advanced through empty slots. */
  @Override
  public void verifyExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BeaconState state,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadVerificationException {
    final ExecutionPayloadEnvelope envelope = signedEnvelope.getMessage();
    final ExecutionPayload payload = envelope.getPayload();

    // Verify signature
    if (!verifyExecutionPayloadEnvelopeSignature(state, signedEnvelope, signatureVerifier)) {
      throw new ExecutionPayloadVerificationException(
          "Signature verification of the execution payload envelope failed");
    }

    // Verify consistency with the beacon block
    if (!envelope.getBeaconBlockRoot().equals(BeaconBlockHeader.fromState(state).hashTreeRoot())) {
      throw new ExecutionPayloadVerificationException(
          "Envelope beacon block root is not consistent with the latest beacon block from the state");
    }
    if (!envelope.getParentBeaconBlockRoot().equals(state.getLatestBlockHeader().getParentRoot())) {
      throw new ExecutionPayloadVerificationException(
          "Envelope parent beacon block root is not consistent with the latest beacon block parent root from the state");
    }
    // A trusted checkpoint can be advanced through empty slots after its latest block.
    if (!envelope.getSlot().equals(state.getLatestBlockHeader().getSlot())) {
      throw new ExecutionPayloadVerificationException(
          "Envelope slot is not consistent with the latest beacon block slot");
    }
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);

    // Verify consistency with the committed bid
    final ExecutionPayloadBid committedBid = stateGloas.getLatestExecutionPayloadBid();

    if (!envelope.getBuilderIndex().equals(committedBid.getBuilderIndex())) {
      throw new ExecutionPayloadVerificationException(
          "Builder index of the envelope is not consistent with the builder index of the committed bid");
    }
    if (!envelope.getPayload().getPrevRandao().equals(committedBid.getPrevRandao())) {
      throw new ExecutionPayloadVerificationException(
          "Prev randao of the envelope is not consistent with the prev randao of the committed bid");
    }
    if (!committedBid.getGasLimit().equals(payload.getGasLimit())) {
      throw new ExecutionPayloadVerificationException(
          "Gas limit of the committed bid is not consistent with the gas limit of the payload");
    }
    if (!committedBid.getBlockHash().equals(payload.getBlockHash())) {
      throw new ExecutionPayloadVerificationException(
          "Block hash of the committed bid is not consistent with the block hash of the payload");
    }
    if (!committedBid
        .getExecutionRequestsRoot()
        .equals(envelope.getExecutionRequests().hashTreeRoot())) {
      throw new ExecutionPayloadVerificationException(
          "Execution requests root of the committed bid is not consistent with the execution requests of the payload");
    }

    // Verify the execution payload is valid
    if (!payload.getParentHash().equals(stateGloas.getLatestBlockHash())) {
      throw new ExecutionPayloadVerificationException(
          "Parent hash of the payload is not consistent with the previous execution payload");
    }
    if (!payload
        .getTimestamp()
        .equals(miscHelpers.computeTimeAtSlot(state.getGenesisTime(), envelope.getSlot()))) {
      throw new ExecutionPayloadVerificationException(
          "Timestamp of the payload is not as expected");
    }
    if (!ExecutionPayloadCapella.required(payload)
        .getWithdrawals()
        .hashTreeRoot()
        .equals(stateGloas.getPayloadExpectedWithdrawals().hashTreeRoot())) {
      throw new ExecutionPayloadVerificationException(
          "Withdrawals of the envelope are not consistent with the expected withdrawals in the state");
    }
    if (payloadExecutor.isPresent()) {
      final NewPayloadRequest payloadToExecute =
          computeNewPayloadRequest(envelope, committedBid.getBlobKzgCommitments());
      final boolean optimisticallyAccept =
          payloadExecutor.get().optimisticallyExecute(Optional.empty(), payloadToExecute);
      if (!optimisticallyAccept) {
        throw new ExecutionPayloadVerificationException(
            "Execution payload was not optimistically accepted");
      }
    }
  }

  @Override
  public boolean verifyExecutionPayloadEnvelopeSignature(
      final BeaconState state,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BLSSignatureVerifier signatureVerifier) {
    final UInt64 builderIndex = signedEnvelope.getMessage().getBuilderIndex();
    final BLSPublicKey pubkey;
    if (builderIndex.equals(BUILDER_INDEX_SELF_BUILD)) {
      final UInt64 validatorIndex = state.getLatestBlockHeader().getProposerIndex();
      pubkey = beaconStateAccessors.getValidatorPubKey(state, validatorIndex).orElseThrow();
    } else {
      pubkey = beaconStateAccessors.getBuilderPubKey(state, builderIndex).orElseThrow();
    }
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.BEACON_BUILDER,
            miscHelpers.computeEpochAtSlot(signedEnvelope.getSlot()));
    final Bytes signingRoot = miscHelpers.computeSigningRoot(signedEnvelope.getMessage(), domain);
    return signatureVerifier.verify(pubkey, signingRoot, signedEnvelope.getSignature());
  }

  protected NewPayloadRequest computeNewPayloadRequest(
      final ExecutionPayloadEnvelope envelope, final SszList<SszKZGCommitment> blobKzgCommitments) {
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    return new NewPayloadRequest(
        envelope.getPayload(),
        versionedHashes,
        envelope.getParentBeaconBlockRoot(),
        executionRequestsDataCodec.encode(envelope.getExecutionRequests()));
  }
}
