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

package tech.pegasys.teku.spec.logic.versions.pq.operations;

import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.OperationSignatureVerifierGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;

/**
 * Operation signatures in PQ. A proposer slashing's headers are signed with XMSS at the proposer
 * leaf of their slot (process_proposer_slashing, modified in PQ). Gossip validation and block
 * processing both check them here.
 */
public class OperationSignatureVerifierPq extends OperationSignatureVerifierGloas {

  private final MiscHelpersPq miscHelpersPq;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;
  private final XmssSignatureVerifier xmssSignatureVerifier;

  public OperationSignatureVerifierPq(
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final PredicatesPq predicates,
      final XmssSignatureVerifier xmssSignatureVerifier) {
    super(miscHelpers, beaconStateAccessors, predicates);
    this.miscHelpersPq = miscHelpers;
    this.beaconStateAccessorsPq = beaconStateAccessors;
    this.xmssSignatureVerifier = xmssSignatureVerifier;
  }

  /** The BLS {@code signatureVerifier} is unused: PQ headers are signed with XMSS. */
  @Override
  public boolean verifyProposerSlashingSignature(
      final Fork fork,
      final BeaconState state,
      final ProposerSlashing proposerSlashing,
      final BLSSignatureVerifier signatureVerifier) {
    final UInt64 proposerIndex = proposerSlashing.getHeader1().getMessage().getProposerIndex();
    if (proposerIndex.isGreaterThanOrEqualTo(state.getValidators().size())
        || !(state.getValidators().get(proposerIndex.intValue()) instanceof ValidatorPq proposer)) {
      return false;
    }
    for (final SignedBeaconBlockHeader signedHeader :
        List.of(proposerSlashing.getHeader1(), proposerSlashing.getHeader2())) {
      if (!isValidHeaderSignature(fork, state, proposer.getXmssPublicKey(), signedHeader)) {
        return false;
      }
    }
    return true;
  }

  private boolean isValidHeaderSignature(
      final Fork fork,
      final BeaconState state,
      final Bytes32 publicKey,
      final SignedBeaconBlockHeader signedHeader) {
    if (!signedHeader.isXmssSigned()) {
      return false;
    }
    final UInt64 slot = signedHeader.getMessage().getSlot();
    final Bytes32 domain =
        beaconStateAccessorsPq.getDomain(
            Domain.BEACON_PROPOSER,
            miscHelpersPq.computeEpochAtSlot(slot),
            fork,
            state.getGenesisValidatorsRoot());
    return xmssSignatureVerifier.verify(
        publicKey,
        miscHelpersPq.computeXmssLeafIndex(slot, LEAF_OFFSET_BEACON_PROPOSER),
        Bytes32.wrap(miscHelpersPq.computeSigningRoot(signedHeader.getMessage(), domain)),
        signedHeader.getXmssSignature());
  }
}
