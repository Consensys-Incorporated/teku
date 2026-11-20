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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/** From PQ, aggregators get the aggregate with its proof and publish it signed with XMSS. */
class AggregatesPqTest extends AbstractMigratedBeaconHandlerTest {

  private PqAttestationSchemas schemas;

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalPq());
    schemas =
        SchemaDefinitionsPq.required(spec.getGenesisSchemaDefinitions()).getPqAttestationSchemas();
  }

  @Test
  void shouldReturnAggregateWithItsProof() throws Exception {
    setHandler(new GetAggregateAttestationV2(validatorDataProvider, schemaDefinitionCache));
    final AttestationPq aggregate = (AttestationPq) dataStructureUtil.randomAttestation();
    final AttestationAndProofPq aggregateWithProof =
        schemas.getAttestationAndProofSchema().create(aggregate, dataStructureUtil.randomBytes(64));
    final UInt64 slot = aggregate.getData().getSlot();
    final Bytes32 dataRoot = aggregate.getData().hashTreeRoot();
    request.setQueryParameter(RestApiConstants.ATTESTATION_DATA_ROOT, dataRoot.toHexString());
    request.setQueryParameter(RestApiConstants.SLOT, slot.toString());
    request.setQueryParameter(RestApiConstants.COMMITTEE_INDEX, "0");
    when(validatorDataProvider.getMilestoneAtSlot(slot)).thenReturn(SpecMilestone.PQ);
    final ObjectAndMetaData<AttestationAndProofPq> response =
        new ObjectAndMetaData<>(aggregateWithProof, SpecMilestone.PQ, false, false, false);
    when(validatorDataProvider.createPqAggregateAndMetaData(slot, dataRoot, UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(response)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(response);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION)).isEqualTo("pq");
  }

  @Test
  void shouldReadAndPublishPqSignedAggregates() throws Exception {
    setHandler(new PostAggregateAndProofsV2(validatorDataProvider, spec, schemaDefinitionCache));
    final SignedAggregateAndProofPq signedAggregate =
        dataStructureUtil.randomSignedAggregateAndProofPq();
    final String json =
        JsonUtil.serialize(
            List.of(signedAggregate),
            DeserializableTypeDefinition.listOf(
                schemas.getSignedAggregateAndProofSchema().getJsonTypeDefinition()));

    final Object requestBody =
        getRequestBodyFromMetadata(handler, Map.of(HEADER_CONSENSUS_VERSION, "pq"), json);
    assertThat(requestBody).isEqualTo(List.of(signedAggregate));

    request.setRequestBody(requestBody);
    when(validatorDataProvider.sendPqAggregateAndProofs(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    verify(validatorDataProvider).sendPqAggregateAndProofs(List.of(signedAggregate));
  }
}
