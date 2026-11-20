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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyBuilderPhase0;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;

/**
 * Builds a PQ {@code BeaconBlockBody}. The RANDAO reveal is the proposer's VRF proof, and the body
 * carries the proof of every attestation's signatures. PQ removed voluntary exits, the sync
 * aggregate, BLS to execution changes and payload attestations, so those may only be left empty,
 * and blocks carry no deposits.
 */
public class BeaconBlockBodyBuilderPq extends BeaconBlockBodyBuilderPhase0 {

  private List<Bytes32> vrfProof;
  private Bytes attestationProof;
  private SignedExecutionPayloadBid signedExecutionPayloadBid;
  private ExecutionRequests parentExecutionRequests;

  public BeaconBlockBodyBuilderPq(final BeaconBlockBodySchemaPq schema) {
    super(schema);
  }

  @Override
  public BeaconBlockBodyBuilder randaoReveal(final BLSSignature randaoReveal) {
    throw new UnsupportedOperationException(
        "A PQ block's RANDAO reveal is a VRF proof: use vrfRandaoReveal");
  }

  @Override
  public boolean supportsVrfRandaoReveal() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder vrfRandaoReveal(final List<Bytes32> vrfProof) {
    this.vrfProof = vrfProof;
    return this;
  }

  @Override
  public boolean supportsAttestationProof() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder attestationProof(final Bytes attestationProof) {
    this.attestationProof = attestationProof;
    return this;
  }

  @Override
  public Boolean supportsSignedExecutionPayloadBid() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder signedExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    this.signedExecutionPayloadBid = signedExecutionPayloadBid;
    return this;
  }

  @Override
  public Boolean supportsParentExecutionRequests() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder parentExecutionRequests(
      final ExecutionRequests parentExecutionRequests) {
    this.parentExecutionRequests = parentExecutionRequests;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder voluntaryExits(final SszList<SignedVoluntaryExit> voluntaryExits) {
    checkArgument(voluntaryExits.isEmpty(), "PQ blocks carry no voluntary exits");
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder syncAggregate(final SyncAggregate syncAggregate) {
    // PQ has no sync committees
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder blsToExecutionChanges(
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
    checkArgument(blsToExecutionChanges.isEmpty(), "PQ blocks carry no BLS to execution changes");
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder payloadAttestations(
      final SszList<PayloadAttestation> payloadAttestations) {
    checkArgument(payloadAttestations.isEmpty(), "PQ blocks carry no payload attestations");
    return this;
  }

  @Override
  protected void validate() {
    checkNotNull(vrfProof, "vrfRandaoReveal must be specified");
    checkNotNull(eth1Data, "eth1Data must be specified");
    checkNotNull(graffiti, "graffiti must be specified");
    checkNotNull(attestations, "attestations must be specified");
    checkNotNull(proposerSlashings, "proposerSlashings must be specified");
    checkNotNull(attesterSlashings, "attesterSlashings must be specified");
    checkNotNull(signedExecutionPayloadBid, "signedExecutionPayloadBid must be specified");
    checkNotNull(parentExecutionRequests, "parentExecutionRequests must be specified");
    checkNotNull(attestationProof, "attestationProof must be specified");
    checkArgument(deposits == null || deposits.isEmpty(), "PQ blocks carry no deposits");
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    final BeaconBlockBodySchemaPq schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaPq.class);
    final BeaconBlockBody emptyBody = schema.createEmpty();
    final List<SszData> fieldValues = new ArrayList<>(emptyBody.size());
    for (int i = 0; i < emptyBody.size(); i++) {
      fieldValues.add(emptyBody.get(i));
    }
    final SszBytes32VectorSchema<?> vrfProofSchema =
        (SszBytes32VectorSchema<?>)
            schema.getChildSchema(schema.getFieldIndex(BlockBodyFields.RANDAO_REVEAL));
    checkArgument(
        vrfProof.size() == vrfProofSchema.getLength(),
        "A VRF proof has %s elements but got %s",
        vrfProofSchema.getLength(),
        vrfProof.size());
    set(schema, fieldValues, BlockBodyFields.RANDAO_REVEAL, vrfProofSchema.of(vrfProof));
    set(schema, fieldValues, BlockBodyFields.ETH1_DATA, eth1Data);
    set(schema, fieldValues, BlockBodyFields.GRAFFITI, SszBytes32.of(graffiti));
    set(
        schema,
        fieldValues,
        BlockBodyFields.PROPOSER_SLASHINGS,
        schema.getProposerSlashingsSchema().createFromElements(proposerSlashings.asList()));
    set(
        schema,
        fieldValues,
        BlockBodyFields.ATTESTER_SLASHINGS,
        schema.getAttesterSlashingsSchema().createFromElements(attesterSlashings.asList()));
    set(
        schema,
        fieldValues,
        BlockBodyFields.ATTESTATIONS,
        schema.getAttestationsSchema().createFromElements(attestations.asList()));
    set(
        schema,
        fieldValues,
        BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID,
        signedExecutionPayloadBid);
    // The parent may be from before the PQ fork, with EIP-8483's ExecutionRequests type
    set(
        schema,
        fieldValues,
        BlockBodyFields.PARENT_EXECUTION_REQUESTS,
        schema.getParentExecutionRequestsSchema().convertFrom(parentExecutionRequests));
    set(
        schema,
        fieldValues,
        BlockBodyFields.PROOF,
        schema.getAttestationProofSchema().fromBytes(attestationProof));
    return schema.createFromFieldValues(fieldValues);
  }

  private static void set(
      final BeaconBlockBodySchemaPq schema,
      final List<SszData> fieldValues,
      final BlockBodyFields field,
      final SszData value) {
    fieldValues.set(schema.getFieldIndex(field), value);
  }
}
