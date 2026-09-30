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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetInclusionListCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetInclusionListCommitteeDuttiesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.HEZE);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void shouldReturnOk() throws IOException {
    final InclusionListDuties inclusionListDuties = randomInclusionListDuties();
    when(validatorApiChannel.getInclusionListDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(inclusionListDuties)));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    final List<UInt64> request = List.of(dataStructureUtil.randomValidatorIndex());

    try (Response response =
        post(
            GetInclusionListCommitteeDuties.ROUTE.replace("{epoch}", "1"),
            JsonUtil.serialize(request, DeserializableTypeDefinition.listOf(UINT64_TYPE)))) {

      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }

  @Test
  void shouldReturnInternalErrorIfEpochIsNotSet() throws IOException {
    final List<UInt64> request = List.of(dataStructureUtil.randomValidatorIndex());

    try (Response response =
        post(
            GetInclusionListCommitteeDuties.ROUTE,
            JsonUtil.serialize(request, DeserializableTypeDefinition.listOf(UINT64_TYPE)))) {

      assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    }

    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws IOException {
    final InclusionListDuties inclusionListDuties = randomInclusionListDuties();
    when(validatorApiChannel.getInclusionListDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(inclusionListDuties)));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    try (Response response =
        post(GetInclusionListCommitteeDuties.ROUTE.replace("{epoch}", "1"), "[]")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
  }

  private InclusionListDuties randomInclusionListDuties() {
    return new InclusionListDuties(
        false, dataStructureUtil.randomBytes32(), List.of(randomInclusionListDuty()));
  }

  private InclusionListDuty randomInclusionListDuty() {
    return new InclusionListDuty(
        dataStructureUtil.randomPublicKey(),
        dataStructureUtil.randomValidatorIndex(),
        dataStructureUtil.randomSlot());
  }
}
