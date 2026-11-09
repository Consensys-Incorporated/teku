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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostPublishExecutionPayloadBid;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostPublishExecutionPayloadBidIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response =
        post(
            PostPublishExecutionPayloadBid.ROUTE,
            "{\"foo\": \"bar\"}",
            Optional.of(SpecMilestone.GLOAS.name()));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    checkEmptyBodyToRoute(PostPublishExecutionPayloadBid.ROUTE, SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnSuccessWhenValidJsonBidIsSubmitted() throws Exception {
    final SignedExecutionPayloadBid bid = dataStructureUtil.randomSignedExecutionPayloadBid();
    when(validatorApiChannel.publishSignedExecutionPayloadBid(any()))
        .thenReturn(SafeFuture.COMPLETE);

    final Response response = postJsonBid(bid);
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnSuccessWhenValidSszBidIsSubmitted() throws Exception {
    final SignedExecutionPayloadBid bid = dataStructureUtil.randomSignedExecutionPayloadBid();
    when(validatorApiChannel.publishSignedExecutionPayloadBid(any()))
        .thenReturn(SafeFuture.COMPLETE);

    final Response response =
        postSsz(
            PostPublishExecutionPayloadBid.ROUTE,
            bid.sszSerialize().toArrayUnsafe(),
            Optional.of(SpecMilestone.GLOAS.name()));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnBadRequestWithRejectionDescriptionWhenValidationFails() throws Exception {
    final SignedExecutionPayloadBid bid = dataStructureUtil.randomSignedExecutionPayloadBid();
    final String rejectionDescription = "Invalid payload execution bid signature";
    final String validationError = "Invalid execution payload bid: " + rejectionDescription;
    when(validatorApiChannel.publishSignedExecutionPayloadBid(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException(validationError)));

    final Response response = postJsonBid(bid);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final JsonNode responseBody = OBJECT_MAPPER.readTree(response.body().string());
    assertThat(responseBody.get("message").asText()).isEqualTo(validationError);
  }

  private Response postJsonBid(final SignedExecutionPayloadBid bid) throws IOException {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions());
    return post(
        PostPublishExecutionPayloadBid.ROUTE,
        JsonUtil.serialize(
            bid, schemaDefinitions.getSignedExecutionPayloadBidSchema().getJsonTypeDefinition()),
        Optional.of(SpecMilestone.GLOAS.name()));
  }
}
