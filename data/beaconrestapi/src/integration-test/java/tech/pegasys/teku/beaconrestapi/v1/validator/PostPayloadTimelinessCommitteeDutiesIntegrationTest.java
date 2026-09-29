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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties.PTC_DUTIES_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.parse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostPayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostPayloadTimelinessCommitteeDutiesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  void shouldErrorIfPriorToGloas() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.FULU);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final Response response =
        post(PostPayloadTimelinessCommitteeDuties.ROUTE.replace("{epoch}", "1"), "");

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("prior to gloas");
  }

  @Test
  void shouldErrorIfSyncing() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final Response response =
        post(PostPayloadTimelinessCommitteeDuties.ROUTE.replace("{epoch}", "1"), "");

    assertThat(response.code()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(response.body().string()).contains("syncing");
  }

  @Test
  void shouldUsePreviousDependentRootForCurrentEpochDuties() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    final PtcDuty duty = new PtcDuty(VALIDATOR_KEYS.get(1).getPublicKey(), ONE, UInt64.valueOf(13));
    final SafeFuture<Optional<PayloadTimelinessCommitteeDuties>> out =
        SafeFuture.completedFuture(
            Optional.of(new PayloadTimelinessCommitteeDuties(false, dependentRoot, List.of(duty))));
    when(validatorApiChannel.getPayloadTimelinessCommitteeDuties(eq(ONE), any())).thenReturn(out);

    final Response response =
        post(PostPayloadTimelinessCommitteeDuties.ROUTE.replace("{epoch}", "1"), "[1]");
    final String responseBody = response.body().string();
    assertThat(responseBody).isNotEmpty();
    assertThat(response.code()).isEqualTo(SC_OK);

    final PayloadTimelinessCommitteeDuties duties = parse(responseBody, PTC_DUTIES_TYPE_DEFINITION);

    assertThat(duties.dependentRoot()).isEqualTo(dependentRoot);
    assertThat(duties.executionOptimistic()).isFalse();
    assertThat(duties.duties().getFirst()).isEqualTo(duty);
  }

  @Test
  void shouldReturnDutiesForNextEpoch() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    final UInt64 nextEpoch = UInt64.valueOf(2);
    final PtcDuty duty = new PtcDuty(VALIDATOR_KEYS.get(1).getPublicKey(), ONE, UInt64.valueOf(13));
    final SafeFuture<Optional<PayloadTimelinessCommitteeDuties>> out =
        SafeFuture.completedFuture(
            Optional.of(new PayloadTimelinessCommitteeDuties(false, dependentRoot, List.of(duty))));
    when(validatorApiChannel.getPayloadTimelinessCommitteeDuties(eq(nextEpoch), any()))
        .thenReturn(out);

    final Response response =
        post(PostPayloadTimelinessCommitteeDuties.ROUTE.replace("{epoch}", "2"), "[1]");
    final String responseBody = response.body().string();
    assertThat(responseBody).isNotEmpty();
    assertThat(response.code()).isEqualTo(SC_OK);

    final PayloadTimelinessCommitteeDuties duties = parse(responseBody, PTC_DUTIES_TYPE_DEFINITION);

    assertThat(duties.dependentRoot()).isEqualTo(dependentRoot);
    assertThat(duties.executionOptimistic()).isFalse();
    assertThat(duties.duties().getFirst()).isEqualTo(duty);
  }
}
