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

import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_PROPOSER_PREFERENCES;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_PTC_ATTESTER;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSignature;
import tech.pegasys.teku.xmss.XmssSigningKey;

/**
 * Signs with a leanXMSS key held in this process. Every signature is first recorded with the {@link
 * XmssLeafProtector}, so a leaf is never used for two different messages, even across restarts.
 * Signing and VRF proofs run on the async runner, as both are CPU-heavy.
 */
public class LocalXmssSigner implements XmssSigner {

  private final Spec spec;
  private final XmssSigningKey signingKey;
  private final VrfTree vrfTree;
  private final XmssLeafProtector leafProtector;
  private final AsyncRunner asyncRunner;
  private final SigningRootUtil signingRootUtil;

  public LocalXmssSigner(
      final Spec spec,
      final XmssSigningKey signingKey,
      final VrfTree vrfTree,
      final XmssLeafProtector leafProtector,
      final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.signingKey = signingKey;
    this.vrfTree = vrfTree;
    this.leafProtector = leafProtector;
    this.asyncRunner = asyncRunner;
    this.signingRootUtil = new SigningRootUtil(spec);
  }

  @Override
  public XmssPublicKey getPublicKey() {
    return signingKey.getPublicKey();
  }

  @Override
  public SafeFuture<XmssSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return sign(
        "block",
        perSlotLeaf(block.getSlot(), LEAF_OFFSET_BEACON_PROPOSER),
        signingRootUtil.signingRootForSignBlock(block, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    final UInt64 targetEpoch = attestationData.getTarget().getEpoch();
    return sign(
        "attestation",
        miscHelpers(attestationData.getSlot()).computeXmssAttestationLeafIndex(targetEpoch),
        signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signAggregateAndProof(
      final AggregateAndProofPq aggregateAndProof, final ForkInfo forkInfo) {
    return sign(
        "aggregate and proof",
        perSlotLeaf(
            aggregateAndProof.getAggregate().getData().getSlot(), LEAF_OFFSET_AGGREGATE_AND_PROOF),
        signingRootUtil.signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signPayloadAttestationData(
      final PayloadAttestationData payloadAttestationData, final ForkInfo forkInfo) {
    return sign(
        "payload attestation",
        perSlotLeaf(payloadAttestationData.getSlot(), LEAF_OFFSET_PTC_ATTESTER),
        signingRootUtil.signingRootForSignPayloadAttestationData(payloadAttestationData, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signProposerPreferences(
      final ProposerPreferences preferences, final ForkInfo forkInfo) {
    return sign(
        "proposer preferences",
        perSlotLeaf(preferences.getProposalSlot(), LEAF_OFFSET_PROPOSER_PREFERENCES),
        signingRootUtil.signingRootForSignProposerPreferences(preferences, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelope envelope, final ForkInfo forkInfo) {
    return sign(
        "execution payload envelope",
        perSlotLeaf(envelope.getSlot(), LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE),
        signingRootUtil.signingRootForSignExecutionPayloadEnvelope(envelope, forkInfo));
  }

  @Override
  public SafeFuture<XmssSignature> signAtReservedLeaf(final Bytes32 signingRoot) {
    return leafProtector
        .allocateReservedLeaf(getPublicKey(), signingRoot)
        .thenCompose(
            leaf ->
                leaf.map(leafIndex -> signAtRecordedLeaf(leafIndex, signingRoot))
                    .orElseGet(
                        () -> SafeFuture.failedFuture(new XmssLeafReuseException(getPublicKey()))));
  }

  @Override
  public SafeFuture<List<Bytes32>> computeVrfProof(final UInt64 slot) {
    return asyncRunner.runAsync(() -> SafeFuture.completedFuture(vrfTree.computeProof(slot)));
  }

  @Override
  public void delete() {
    signingKey.close();
  }

  private SafeFuture<XmssSignature> sign(
      final String messageType, final UInt64 leafIndex, final Bytes signingRoot) {
    final Bytes32 message = Bytes32.wrap(signingRoot);
    return leafProtector
        .maySign(getPublicKey(), leafIndex, message)
        .thenCompose(
            maySign ->
                maySign
                    ? signAtRecordedLeaf(leafIndex, message)
                    : SafeFuture.failedFuture(
                        new XmssLeafReuseException(messageType, getPublicKey(), leafIndex)));
  }

  private SafeFuture<XmssSignature> signAtRecordedLeaf(
      final UInt64 leafIndex, final Bytes32 message) {
    return asyncRunner.runAsync(
        () -> SafeFuture.completedFuture(signingKey.sign(leafIndex, message)));
  }

  private UInt64 perSlotLeaf(final UInt64 slot, final UInt64 leafOffset) {
    return miscHelpers(slot).computeXmssLeafIndex(slot, leafOffset);
  }

  private MiscHelpersPq miscHelpers(final UInt64 slot) {
    if (!(spec.atSlot(slot).miscHelpers() instanceof MiscHelpersPq miscHelpersPq)) {
      throw new IllegalArgumentException(
          "Slot " + slot + " is before the PQ fork, so its messages are signed with BLS");
    }
    return miscHelpersPq;
  }
}
