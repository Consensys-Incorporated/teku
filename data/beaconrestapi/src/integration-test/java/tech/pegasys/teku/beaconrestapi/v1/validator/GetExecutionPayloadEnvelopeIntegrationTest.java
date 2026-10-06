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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetExecutionPayloadEnvelope;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetExecutionPayloadEnvelopeIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private ExecutionPayloadEnvelope envelope;

  @BeforeEach
  void setUp() {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    envelope = dataStructureUtil.randomExecutionPayloadEnvelope();
  }

  @Test
  void shouldReturnExecutionPayloadEnvelopeAsJson() throws IOException {
    when(validatorApiChannel.createUnsignedExecutionPayload(
            envelope.getSlot(), envelope.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(envelope)));

    final Response response = get(envelope, JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");

    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());
    assertThat(body.get("version").asText()).isEqualTo("gloas");

    final ExecutionPayloadEnvelope parsed =
        JsonUtil.parse(
            body.get("data").toString(),
            SchemaDefinitionsGloas.required(
                    spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions())
                .getExecutionPayloadEnvelopeSchema()
                .getJsonTypeDefinition());
    assertThat(parsed).isEqualTo(envelope);
  }

  @Test
  void shouldReturnExecutionPayloadEnvelopeAsSsz() throws IOException {
    when(validatorApiChannel.createUnsignedExecutionPayload(
            envelope.getSlot(), envelope.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(envelope)));

    final Response response = get(envelope, OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");
    assertThat(response.body().bytes()).isEqualTo(envelope.sszSerialize().toArrayUnsafe());
  }

  @Test
  void shouldReturn404WhenNoEnvelopeRetrieved() throws IOException {
    when(validatorApiChannel.createUnsignedExecutionPayload(
            envelope.getSlot(), envelope.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Response response = get(envelope, JSON);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  private Response get(final ExecutionPayloadEnvelope envelope, final String contentType)
      throws IOException {
    return getResponse(
        GetExecutionPayloadEnvelope.ROUTE
            .replace("{slot}", envelope.getSlot().toString())
            .replace("{beacon_block_root}", envelope.getBeaconBlockRoot().toHexString()),
        contentType);
  }
}
