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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.events.SyncState.IN_SYNC;
import static tech.pegasys.teku.beacon.sync.events.SyncState.SYNCING;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;

public class PostSyncDutiesTest extends AbstractMigratedBeaconHandlerTest {
  private final SyncCommitteeDuty duty =
      new SyncCommitteeDuty(dataStructureUtil.randomPublicKey(), 1, IntArraySet.of(1, 2));
  private final SyncCommitteeDuties responseData = new SyncCommitteeDuties(false, List.of(duty));

  @BeforeEach
  void setup() {
    setHandler(new PostSyncDuties(syncDataProvider, validatorDataProvider));
    request.setPathParameter("epoch", "1");
  }

  @Test
  void shouldBeAbleToSubmitSyncDuties() throws Exception {
    final List<Integer> requestBody = List.of(1, 2);
    request.setRequestBody(requestBody);

    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(IN_SYNC);
    when(validatorDataProvider.getSyncDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(responseData)));

    handler.handleRequest(request);

    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    Assertions.assertThat(request.getResponseBody()).isEqualTo(responseData);
  }

  @Test
  void shouldRespondSyncing() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SYNCING);
    handler.handleRequest(request);

    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    Assertions.assertThat(request.getResponseBody()).isInstanceOf(HttpErrorResponse.class);
  }

  @Test
  void shouldReadRequestBody() throws IOException {
    final String data = "[\"1\"]";
    Assertions.assertThat(getRequestBodyFromMetadata(handler, data)).isEqualTo(List.of(1));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(PostSyncDutiesTest.class, "postSyncDuties.json"), UTF_8);
    assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
