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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;

public class PostSyncCommitteeSubscriptionsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    checkEmptyBodyToRoute(PostSyncCommitteeSubscriptions.ROUTE, SC_BAD_REQUEST);
  }

  @Test
  void shouldPostSubscriptions() throws IOException {
    when(validatorApiChannel.subscribeToSyncCommitteeSubnets(any()))
        .thenReturn(SafeFuture.COMPLETE);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response =
        post(
            PostSyncCommitteeSubscriptions.ROUTE,
            "[{\"validator_index\":\"1\",\"sync_committee_indices\":[\"1\"],\"until_epoch\":\"1\"}]");
    assertThat(response.code()).isEqualTo(SC_OK);
  }
}
