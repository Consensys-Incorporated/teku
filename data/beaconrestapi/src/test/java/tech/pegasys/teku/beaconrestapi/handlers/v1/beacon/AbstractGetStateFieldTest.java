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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public abstract class AbstractGetStateFieldTest<T extends SszCollection<?>>
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  private T seededField;

  protected abstract SpecMilestone getMinimumMilestone();

  protected abstract String getFieldLabel();

  protected abstract AbstractGetStateField<T> createHandler(
      ChainDataProvider provider, SchemaDefinitionCache schemaDefinitionCache);

  protected abstract T getField(BeaconState state);

  protected abstract BeaconState withField(BeaconState state, T field);

  /** Creates a non-default value of the field for the current {@code spec}. */
  protected abstract T createField();

  @BeforeEach
  void setUpAtGenesisWithSeededField() {
    initialise(getMinimumMilestone());
    seededField = createField();
    genesis(state -> withField(state, seededField));
    setUpHandler();
  }

  private void setUpHandler() {
    setHandler(createHandler(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter("state_id", "head");
  }

  @Test
  void shouldReturnFieldFromHeadState() throws JsonProcessingException {
    assertThat(seededField).isNotEqualTo(seededField.getSchema().getDefault());
    assertThat(getField(safeJoin(recentChainData.getBestState().orElseThrow())))
        .isEqualTo(seededField);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(getMinimumMilestone().lowerCaseName());
    final ObjectAndMetaData<?> response = (ObjectAndMetaData<?>) request.getResponseBody();
    assertThat(response.getMilestone()).isEqualTo(getMinimumMilestone());
    assertThat(response.getData()).isEqualTo(seededField);
  }

  @Test
  void shouldRejectStateBeforeMinimumMilestone() throws JsonProcessingException {
    initialise(getMinimumMilestone().getPreviousMilestone());
    genesis();
    setUpHandler();

    handler.handleRequest(request);

    assertThat(request.getResponseError())
        .hasValueSatisfying(
            error ->
                assertThat(error)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(
                        "The state was successfully retrieved, but was prior to %s and does not contain %s.",
                        getMinimumMilestone().name(), getFieldLabel()));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200Json() throws Exception {
    final T field = createField();
    assertThat(field).isNotEqualTo(field.getSchema().getDefault());

    final String json =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(field, getMinimumMilestone(), true, true, false));

    final JsonNode node = JsonTestUtil.parseAsJsonNode(json);
    assertThat(node.get("version").asText()).isEqualTo(getMinimumMilestone().lowerCaseName());
    assertThat(node.get("execution_optimistic").asBoolean()).isTrue();
    assertThat(node.get("finalized").asBoolean()).isFalse();
    assertThat(
            JsonUtil.parse(node.get("data").toString(), field.getSchema().getJsonTypeDefinition()))
        .isEqualTo(field);
  }

  @Test
  void metadata_shouldHandle200OctetStream() throws IOException {
    final T field = createField();

    final byte[] ssz =
        getResponseSszFromMetadata(
            handler,
            SC_OK,
            new ObjectAndMetaData<>(field, getMinimumMilestone(), false, true, false));

    assertThat(Bytes.of(ssz)).isEqualTo(field.sszSerialize());
  }
}
