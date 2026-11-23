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

package tech.pegasys.teku.spec.signatures.xmss;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSignature;

/**
 * Signs a validator's messages from the PQ fork on, with its leanXMSS key, and produces its VRF
 * proofs (PQ validator.md). Each message is signed at the leaf the spec assigns it. Signing fails
 * with {@link XmssLeafReuseException} rather than sign a different message at a used leaf.
 *
 * <p>The BLS {@link tech.pegasys.teku.spec.signatures.Signer} keeps signing the validator's
 * messages from before the fork.
 */
public interface XmssSigner {

  XmssPublicKey getPublicKey();

  /** get_block_signature: at the block's slot, offset {@code LEAF_OFFSET_BEACON_PROPOSER}. */
  SafeFuture<XmssSignature> signBlock(BeaconBlock block, ForkInfo forkInfo);

  /** get_attestation_signature: at the target epoch's attestation leaf. */
  SafeFuture<XmssSignature> signAttestationData(AttestationData attestationData, ForkInfo forkInfo);

  /** get_aggregate_and_proof_signature: offset {@code LEAF_OFFSET_AGGREGATE_AND_PROOF}. */
  SafeFuture<XmssSignature> signAggregateAndProof(
      AggregateAndProofPq aggregateAndProof, ForkInfo forkInfo);

  /** get_payload_attestation_message_signature: offset {@code LEAF_OFFSET_PTC_ATTESTER}. */
  SafeFuture<XmssSignature> signPayloadAttestationData(
      PayloadAttestationData payloadAttestationData, ForkInfo forkInfo);

  /**
   * get_signed_proposer_preferences: at the proposal slot, offset {@code
   * LEAF_OFFSET_PROPOSER_PREFERENCES}. Preferences for a slot are always signed at the same leaf,
   * so a second, different preferences message for the slot is refused.
   */
  SafeFuture<XmssSignature> signProposerPreferences(
      ProposerPreferences preferences, ForkInfo forkInfo);

  /** A self-built envelope: offset {@code LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE}. */
  SafeFuture<XmssSignature> signExecutionPayloadEnvelope(
      ExecutionPayloadEnvelope envelope, ForkInfo forkInfo);

  /**
   * Signs a message without a slot of its own, such as a preregistration or a voluntary exit
   * request, at the next unused reserved leaf.
   */
  SafeFuture<XmssSignature> signAtReservedLeaf(Bytes32 signingRoot);

  /**
   * Gets the key ready to sign at {@code slot} without delay, by building the bottom trees its
   * leaves are in ({@link XmssLeafLayout#bottomTreesAt}). Building a tree takes seconds, so call
   * this ahead of the slot. Does nothing for trees the key already holds, and before the PQ fork.
   *
   * @return how many trees the key had to build
   */
  SafeFuture<Integer> prepareForSlot(UInt64 slot);

  /** compute_vrf_proof: the RANDAO reveal of a block at {@code slot}. */
  SafeFuture<List<Bytes32>> computeVrfProof(UInt64 slot);

  /** Releases the key. Signing afterwards fails. */
  void delete();
}
