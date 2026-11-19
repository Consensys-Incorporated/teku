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

package tech.pegasys.teku.spec.logic.versions.pq.execution;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionPayloadVerifierGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;

/** The Gloas envelope verification with the envelope signed by XMSS. */
public class ExecutionPayloadVerifierPq extends ExecutionPayloadVerifierGloas {

  private final MiscHelpersPq miscHelpers;
  private final BeaconStateAccessorsPq beaconStateAccessors;
  private final XmssSignatureVerifier xmssSignatureVerifier;

  public ExecutionPayloadVerifierPq(
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final XmssSignatureVerifier xmssSignatureVerifier) {
    super(miscHelpers, beaconStateAccessors, executionRequestsDataCodec);
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.xmssSignatureVerifier = xmssSignatureVerifier;
  }

  // verify_execution_payload_envelope_signature, modified in PQ
  @Override
  protected boolean verifyExecutionPayloadEnvelopeSignature(
      final BeaconState state,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BLSSignatureVerifier signatureVerifier,
      final UInt64 signatureSlot) {
    if (!signedEnvelope.isXmssSigned()) {
      return false;
    }
    final UInt64 builderIndex = signedEnvelope.getMessage().getBuilderIndex();
    final Bytes32 pubkey;
    if (builderIndex.equals(BUILDER_INDEX_SELF_BUILD)) {
      final int validatorIndex = state.getLatestBlockHeader().getProposerIndex().intValue();
      pubkey = ((ValidatorPq) state.getValidators().get(validatorIndex)).getXmssPublicKey();
    } else {
      pubkey =
          ((BuilderPq) BeaconStateGloas.required(state).getBuilders().get(builderIndex.intValue()))
              .getXmssPublicKey();
    }
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.BEACON_BUILDER,
            miscHelpers.computeEpochAtSlot(signatureSlot));
    final Bytes32 signingRoot =
        Bytes32.wrap(miscHelpers.computeSigningRoot(signedEnvelope.getMessage(), domain));
    final UInt64 leafIndex =
        miscHelpers.computeXmssLeafIndex(
            state.getLatestBlockHeader().getSlot(), LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE);
    return xmssSignatureVerifier.verify(
        pubkey, leafIndex, signingRoot, signedEnvelope.getXmssSignature());
  }
}
