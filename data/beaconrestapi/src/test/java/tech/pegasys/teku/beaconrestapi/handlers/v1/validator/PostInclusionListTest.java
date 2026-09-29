/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostInclusionListTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    spec = TestSpecFactory.createMinimalHeze();
    dataStructureUtil = new DataStructureUtil(spec);
    schemaDefinitionCache = new SchemaDefinitionCache(spec);
    setHandler(new PostInclusionList(validatorDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldBeAbleToSubmitSignedInclusionList() throws Exception {
    final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
    request.setRequestBody(signedInclusionList);

    when(validatorDataProvider.sendSignedInclusionList(anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReadWrappedRequestBody() throws IOException {
    final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
    final SignedInclusionListSchema signedInclusionListSchema =
        schemaDefinitionCache
            .getSchemaDefinition(HEZE)
            .toVersionHeze()
            .orElseThrow()
            .getSignedInclusionListSchema();
    final String data =
        JsonUtil.serialize(signedInclusionList, signedInclusionListSchema.getJsonTypeDefinition());

    Assertions.assertThat(getRequestBodyFromMetadata(handler, "{\"data\":" + data + "}"))
        .isEqualTo(signedInclusionList);
  }

  @Test
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }
}
