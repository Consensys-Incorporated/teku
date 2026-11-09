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
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostSyncCommitteesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private static final String ERROR_MESSAGE = "The Error Description";

  @Test
  void shouldSubmitSyncCommitteesAndGetResponse() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final SafeFuture<List<SubmitDataError>> future =
        SafeFuture.completedFuture(List.of(new SubmitDataError(UInt64.ZERO, ERROR_MESSAGE)));
    when(validatorApiChannel.sendSyncCommitteeMessages(any())).thenReturn(future);
    final Response response =
        post(
            PostSyncCommittees.ROUTE,
            JsonUtil.serialize(
                List.of(dataStructureUtil.randomSyncCommitteeMessage()),
                listOf(message.getSchema().getJsonTypeDefinition())));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());
    assertThat(body.get("failures").get(0).get("message").asText()).isEqualTo(ERROR_MESSAGE);
  }

  @Test
  void shouldGet200OkWhenThereAreNoErrors() throws Exception {
    spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final SafeFuture<List<SubmitDataError>> future = SafeFuture.completedFuture(List.of());
    when(validatorApiChannel.sendSyncCommitteeMessages(any())).thenReturn(future);
    final Response response =
        post(
            PostSyncCommittees.ROUTE,
            JsonUtil.serialize(
                List.of(dataStructureUtil.randomSyncCommitteeMessage()),
                listOf(message.getSchema().getJsonTypeDefinition())));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  void phase0SlotCausesBadRequest() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Response response =
        post(
            PostSyncCommittees.ROUTE,
            String.format(
                "[{\"slot\":\"1\",\"beacon_block_root\":\"%s\",\"validator_index\":\"1\",\"signature\":\"%s\"}]",
                dataStructureUtil.randomBytes32(), dataStructureUtil.randomSignature()));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .contains("Could not create sync committee signature at phase0 slot 1");
  }
}
