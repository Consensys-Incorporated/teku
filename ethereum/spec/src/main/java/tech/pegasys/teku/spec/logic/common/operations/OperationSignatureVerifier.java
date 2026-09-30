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

package tech.pegasys.teku.spec.logic.common.operations;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

public class OperationSignatureVerifier {

  private static final Logger LOG = LogManager.getLogger();

  private final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;

  public OperationSignatureVerifier(
      final MiscHelpers miscHelpers, final BeaconStateAccessors beaconStateAccessors) {
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  public boolean verifyProposerSlashingSignature(
      final Fork fork,
      final BeaconState state,
      final ProposerSlashing proposerSlashing,
      final BLSSignatureVerifier signatureVerifier) {

    final BeaconBlockHeader header1 = proposerSlashing.getHeader1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader2().getMessage();

    Optional<BLSPublicKey> maybePublicKey =
        beaconStateAccessors.getValidatorPubKey(state, header1.getProposerIndex());
    if (maybePublicKey.isEmpty()) {
      return false;
    }
    BLSPublicKey publicKey = maybePublicKey.get();

    if (!signatureVerifier.verify(
        publicKey,
        miscHelpers.computeSigningRoot(
            header1,
            beaconStateAccessors.getDomain(
                Domain.BEACON_PROPOSER,
                miscHelpers.computeEpochAtSlot(header1.getSlot()),
                fork,
                state.getGenesisValidatorsRoot())),
        proposerSlashing.getHeader1().getSignature())) {
      LOG.trace("Header1 signature is invalid {}", header1);
      return false;
    }

    if (!signatureVerifier.verify(
        publicKey,
        miscHelpers.computeSigningRoot(
            header2,
            beaconStateAccessors.getDomain(
                Domain.BEACON_PROPOSER,
                miscHelpers.computeEpochAtSlot(header2.getSlot()),
                fork,
                state.getGenesisValidatorsRoot())),
        proposerSlashing.getHeader2().getSignature())) {
      LOG.trace("Header2 signature is invalid {}", header1);
      return false;
    }
    return true;
  }

  public boolean verifyVoluntaryExitSignature(
      final BeaconState state,
      final SignedVoluntaryExit signedExit,
      final BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();

    final Optional<BLSPublicKey> maybePublicKey =
        beaconStateAccessors.getValidatorPubKey(state, exit.getValidatorIndex());
    if (maybePublicKey.isEmpty()) {
      return false;
    }

    final Bytes32 domain =
        beaconStateAccessors.getVoluntaryExitDomain(
            exit.getEpoch(), state.getFork(), state.getGenesisValidatorsRoot());

    final Bytes signingRoot = miscHelpers.computeSigningRoot(exit, domain);
    return signatureVerifier.verify(maybePublicKey.get(), signingRoot, signedExit.getSignature());
  }

  public boolean verifyBlsToExecutionChangeSignature(
      final BeaconState state,
      final SignedBlsToExecutionChange signedBlsToExecutionChange,
      final BLSSignatureVerifier signatureVerifier) {
    final BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();
    final BLSPublicKey publicKey = addressChange.getFromBlsPubkey();
    final Bytes signingRoot = calculateBlsToExecutionChangeSigningRoot(state, addressChange);
    final BLSSignature signature = signedBlsToExecutionChange.getSignature();

    return signatureVerifier.verify(publicKey, signingRoot, signature);
  }

  public SafeFuture<Boolean> verifyBlsToExecutionChangeSignatureAsync(
      final BeaconState state,
      final SignedBlsToExecutionChange signedBlsToExecutionChange,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();
    final BLSPublicKey publicKey = addressChange.getFromBlsPubkey();
    final Bytes signingRoot = calculateBlsToExecutionChangeSigningRoot(state, addressChange);
    final BLSSignature signature = signedBlsToExecutionChange.getSignature();

    return signatureVerifier.verify(publicKey, signingRoot, signature);
  }

  private Bytes calculateBlsToExecutionChangeSigningRoot(
      final BeaconState state, final BlsToExecutionChange addressChange) {
    final Bytes32 domain =
        miscHelpers.computeDomain(Domain.BLS_TO_EXECUTION_CHANGE, state.getGenesisValidatorsRoot());
    return miscHelpers.computeSigningRoot(addressChange, domain);
  }

  public boolean verifyExecutionPayloadBidSignature(
      final BeaconState state,
      final SignedExecutionPayloadBid signedBid,
      final BLSSignatureVerifier signatureVerifier) {
    final BLSPublicKey builderPubkey =
        BeaconStateGloas.required(state)
            .getBuilders()
            .get(signedBid.getMessage().getBuilderIndex().intValue())
            .getPublicKey();
    final Bytes signingRoot =
        calculateExecutionPayloadBidSigningRoot(state, signedBid.getMessage());
    return signatureVerifier.verify(builderPubkey, signingRoot, signedBid.getSignature());
  }

  private Bytes calculateExecutionPayloadBidSigningRoot(
      final BeaconState state, final ExecutionPayloadBid bid) {
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.BEACON_BUILDER,
            miscHelpers.computeEpochAtSlot(state.getSlot()));
    return miscHelpers.computeSigningRoot(bid, domain);
  }
}
