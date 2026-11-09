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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;

public class PostAttesterDutiesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  void shouldGiveDecentErrorIfNoBody() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    Response response = post(PostAttesterDuties.ROUTE.replace("{epoch}", "1"), "");

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("Array expected but got null");
  }

  @Test
  void shouldGiveDecentErrorIfEmptyArray() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    Response response = post(PostAttesterDuties.ROUTE.replace("{epoch}", "1"), "[]");

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .contains("Provided array has less than 1 minimum required items");
  }

  @Test
  void shouldBeOkWithCorrectInput() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(validatorApiChannel.getAttestationDuties(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new AttesterDuties(false, Bytes32.ZERO, List.of()))));

    final Response response = post(PostAttesterDuties.ROUTE.replace("{epoch}", "1"), "[1]");

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string())
        .contains(
            "{\"dependent_root\":\"0x0000000000000000000000000000000000000000000000000000000000000000\","
                + "\"execution_optimistic\":false,\"data\":[]}");
  }
}
