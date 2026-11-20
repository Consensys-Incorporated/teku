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

import static tech.pegasys.teku.spec.constants.PqConstants.VRF_TREE_DEPTH;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsSchemaPq;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;

/**
 * {@code BeaconBlockBody} in PQ: a progressive container 14 fields wide with gaps at 7, 8, 9 and 11
 * (voluntary exits, sync aggregate, BLS changes and payload attestations).
 */
public class BeaconBlockBodySchemaPq
    extends ContainerSchema10<
        BeaconBlockBodyPq,
        SszBytes32Vector,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SignedExecutionPayloadBid,
        ExecutionRequests,
        SszByteList>
    implements BeaconBlockBodySchema<BeaconBlockBodyPq> {

  private static final boolean[] ACTIVE_FIELDS = {
    true, true, true, true, true, true, true, false, false, false, true, false, true, true
  };

  @SuppressWarnings("unchecked")
  public BeaconBlockBodySchemaPq(
      final String containerName,
      final SpecConfigPq specConfig,
      final PqAttestationSchemas attestationSchemas,
      final SignedExecutionPayloadBidSchema signedExecutionPayloadBidSchema,
      final ExecutionRequestsSchemaPq executionRequestsSchema) {
    super(
        containerName,
        ACTIVE_FIELDS,
        namedSchema(
            BlockBodyFields.RANDAO_REVEAL, SszBytes32VectorSchema.create(VRF_TREE_DEPTH + 1)),
        namedSchema(BlockBodyFields.ETH1_DATA, Eth1Data.SSZ_SCHEMA),
        namedSchema(BlockBodyFields.GRAFFITI, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            BlockBodyFields.PROPOSER_SLASHINGS,
            SszProgressiveListSchema.create(
                ProposerSlashing.SSZ_SCHEMA_PQ, specConfig.getMaxProposerSlashings())),
        namedSchema(
            BlockBodyFields.ATTESTER_SLASHINGS,
            SszProgressiveListSchema.create(
                attestationSchemas.getAttesterSlashingSchema(),
                specConfig.getMaxAttesterSlashingsElectra())),
        namedSchema(
            BlockBodyFields.ATTESTATIONS,
            (SszSchema<SszList<Attestation>>)
                (SszSchema<?>)
                    SszProgressiveListSchema.create(
                        attestationSchemas.getAttestationSchema(),
                        specConfig.getMaxAttestationsPq())),
        namedSchema(
            BlockBodyFields.DEPOSITS,
            // As in Gloas, blocks carry no deposits (spec `Deposits.LIMIT = 0`)
            SszProgressiveListSchema.create(Deposit.SSZ_SCHEMA, 0)),
        namedSchema(BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID, signedExecutionPayloadBidSchema),
        namedSchema(
            BlockBodyFields.PARENT_EXECUTION_REQUESTS,
            SszSchema.as(ExecutionRequests.class, executionRequestsSchema)),
        namedSchema(BlockBodyFields.PROOF, attestationSchemas.getAttestationProofSchema()));
  }

  /** Building PQ block bodies comes with PQ's block production. */
  @Override
  public SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder) {
    throw new UnsupportedOperationException("PQ block production is not implemented yet");
  }

  @Override
  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBodyPq(this);
  }

  /**
   * As in Gloas, the genesis body is an empty body whose (unsigned) bid is the state's {@code
   * latest_execution_payload_bid}; in PQ the empty signature is an all-zero XMSS signature.
   */
  @Override
  public BeaconBlockBody createGenesisBody(final BeaconState genesisState) {
    final ExecutionPayloadBid latestExecutionPayloadBid =
        BeaconStateGloas.required(genesisState).getLatestExecutionPayloadBid();
    final SignedExecutionPayloadBid signedExecutionPayloadBid =
        getSignedExecutionPayloadBidSchema()
            .create(latestExecutionPayloadBid, SszXmssSignature.empty());
    final BeaconBlockBody emptyBody = createEmpty();
    final List<SszData> fieldValues = new ArrayList<>(emptyBody.size());
    for (int i = 0; i < emptyBody.size(); i++) {
      fieldValues.add(emptyBody.get(i));
    }
    fieldValues.set(
        getFieldIndex(BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID), signedExecutionPayloadBid);
    return createFromFieldValues(fieldValues);
  }

  public SignedExecutionPayloadBidSchema getSignedExecutionPayloadBidSchema() {
    return (SignedExecutionPayloadBidSchema)
        getChildSchema(getFieldIndex(BlockBodyFields.SIGNED_EXECUTION_PAYLOAD_BID));
  }

  public SszByteListSchema<?> getAttestationProofSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(BlockBodyFields.PROOF));
  }

  @Override
  public BeaconBlockBodyPq createFromBackingNode(final TreeNode node) {
    return new BeaconBlockBodyPq(this, node);
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
    return (SszListSchema<ProposerSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.PROPOSER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
    return (SszListSchema<AttesterSlashing, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTER_SLASHINGS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Attestation, ?> getAttestationsSchema() {
    return (SszListSchema<Attestation, ?>)
        getChildSchema(getFieldIndex(BlockBodyFields.ATTESTATIONS));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return (SszListSchema<Deposit, ?>) getChildSchema(getFieldIndex(BlockBodyFields.DEPOSITS));
  }

  /** PQ blocks have no voluntary exits. */
  @Override
  public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
    throw new UnsupportedOperationException("PQ blocks have no voluntary exits");
  }
}
