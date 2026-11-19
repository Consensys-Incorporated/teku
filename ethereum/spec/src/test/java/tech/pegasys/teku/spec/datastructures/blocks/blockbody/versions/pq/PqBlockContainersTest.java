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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PqBlockContainersTest {

  // latest_block_header.body_root in the PQ genesis of the ethpandaops genesis generator
  private static final Bytes32 FIXTURE_GENESIS_BODY_ROOT =
      Bytes32.fromHexString("0x36a7c9d17b839934edf1c018e1cba984ba1068610d33cbebb9f77f765377304d");

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions());
  private final PqBlockSchemas schemas = schemaDefinitions.getPqBlockSchemas();

  @Test
  void genesisBodyMatchesAnIndependentImplementation() throws IOException {
    final BeaconState genesisState =
        schemaDefinitions
            .getPqBeaconStateSchema()
            .sszDeserialize(
                Bytes.wrap(
                    Resources.toByteArray(
                        Resources.getResource(
                            "tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/pq/"
                                + "pq_genesis_minimal.ssz"))));

    final BeaconBlockBody genesisBody =
        schemas.getBeaconBlockBodySchema().createGenesisBody(genesisState);

    assertThat(genesisBody.hashTreeRoot()).isEqualTo(FIXTURE_GENESIS_BODY_ROOT);
    assertThat(genesisState.getLatestBlockHeader().getBodyRoot())
        .isEqualTo(FIXTURE_GENESIS_BODY_ROOT);
  }

  @Test
  void bodyDropsFourFieldsAndAddsTheProof() {
    final BeaconBlockBodySchemaPq schema = schemas.getBeaconBlockBodySchema();
    assertThat(schema.getFieldNames())
        .containsExactly(
            "randao_reveal",
            "eth1_data",
            "graffiti",
            "proposer_slashings",
            "attester_slashings",
            "attestations",
            "deposits",
            "signed_execution_payload_bid",
            "parent_execution_requests",
            "proof");

    final BeaconBlockBodyPq body = BeaconBlockBodyPq.required(schema.createEmpty());

    assertThat(body.getVrfProof()).hasSize(PqConstants.VRF_TREE_DEPTH + 1);
    assertThat(body.getProof()).isEqualTo(Bytes.EMPTY);
    assertThat(body.getOptionalSignedExecutionPayloadBid()).isPresent();
    assertThat(body.getOptionalSyncAggregate()).isEmpty();
    assertThatThrownBy(body::getRandaoReveal).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(body::getVoluntaryExits).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(schema::getVoluntaryExitsSchema)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void signedBlockIsXmssSigned() {
    final BeaconBlock block =
        schemas
            .getBeaconBlockSchema()
            .create(
                UInt64.valueOf(9),
                UInt64.valueOf(3),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                schemas.getBeaconBlockBodySchema().createEmpty());
    final SignedBeaconBlock signedBlock =
        schemas.getSignedBeaconBlockSchema().create(block, xmssSignature(7));

    final SignedBeaconBlock roundTripped =
        schemas.getSignedBeaconBlockSchema().sszDeserialize(signedBlock.sszSerialize());

    assertThat(roundTripped).isEqualTo(signedBlock);
    assertThat(roundTripped.isXmssSigned()).isTrue();
    assertThat(roundTripped.getXmssSignature()).isEqualTo(xmssSignature(7).getBytes());
    assertThatThrownBy(roundTripped::getSignature)
        .isInstanceOf(UnsupportedOperationException.class);

    final SignedBeaconBlockHeader header = roundTripped.asHeader();
    assertThat(header.getSchema()).isSameAs(SignedBeaconBlockHeader.SSZ_SCHEMA_PQ);
    assertThat(header.getMessage().getBodyRoot()).isEqualTo(block.getBodyRoot());
    assertThat(header.getXmssSignature()).isEqualTo(xmssSignature(7).getBytes());
  }

  @Test
  void proposerSlashingFollowsItsHeaders() {
    final SignedBeaconBlockHeader header1 =
        new SignedBeaconBlockHeader(dataStructureUtil.randomBeaconBlockHeader(), xmssSignature(1));
    final SignedBeaconBlockHeader header2 =
        new SignedBeaconBlockHeader(dataStructureUtil.randomBeaconBlockHeader(), xmssSignature(2));

    final ProposerSlashing slashing = new ProposerSlashing(header1, header2);

    assertThat(slashing.getSchema()).isSameAs(ProposerSlashing.SSZ_SCHEMA_PQ);
    assertThat(ProposerSlashing.SSZ_SCHEMA_PQ.sszDeserialize(slashing.sszSerialize()))
        .isEqualTo(slashing);
    // A BLS-signed slashing keeps the schema of every fork up to PQ
    final BeaconBlockHeader message = dataStructureUtil.randomBeaconBlockHeader();
    final ProposerSlashing blsSlashing =
        new ProposerSlashing(
            new SignedBeaconBlockHeader(message, dataStructureUtil.randomSignature()),
            new SignedBeaconBlockHeader(message, dataStructureUtil.randomSignature()));
    assertThat(blsSlashing.getSchema()).isSameAs(ProposerSlashing.SSZ_SCHEMA);
    assertThat(blsSlashing.getHeader1().isXmssSigned()).isFalse();
    assertThatThrownBy(() -> blsSlashing.getHeader1().getXmssSignature())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void bidEnvelopeAndPreferencesAreXmssSigned() {
    final SignedExecutionPayloadBid bid =
        schemas
            .getSignedExecutionPayloadBidSchema()
            .create(dataStructureUtil.randomExecutionPayloadBid(), xmssSignature(1));
    final SignedExecutionPayloadEnvelope envelope =
        schemas
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(dataStructureUtil.randomExecutionPayloadEnvelope(), xmssSignature(2));
    final SignedProposerPreferences preferences =
        schemas
            .getSignedProposerPreferencesSchema()
            .create(dataStructureUtil.randomProposerPreferences(), xmssSignature(3));

    assertThat(schemas.getSignedExecutionPayloadBidSchema().sszDeserialize(bid.sszSerialize()))
        .isEqualTo(bid);
    assertThat(
            schemas
                .getSignedExecutionPayloadEnvelopeSchema()
                .sszDeserialize(envelope.sszSerialize()))
        .isEqualTo(envelope);
    assertThat(
            schemas.getSignedProposerPreferencesSchema().sszDeserialize(preferences.sszSerialize()))
        .isEqualTo(preferences);
    for (final Bytes signature :
        List.of(
            bid.getXmssSignature(), envelope.getXmssSignature(), preferences.getXmssSignature())) {
      assertThat(signature.size()).isEqualTo(2404);
    }
    assertThatThrownBy(bid::getSignature).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(envelope::getSignature).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(preferences::getSignature).isInstanceOf(UnsupportedOperationException.class);
  }

  private static SszXmssSignature xmssSignature(final int seed) {
    final byte[] signature = new byte[2404];
    signature[0] = (byte) seed;
    signature[2403] = (byte) seed;
    return new SszXmssSignature(Bytes.wrap(signature));
  }
}
