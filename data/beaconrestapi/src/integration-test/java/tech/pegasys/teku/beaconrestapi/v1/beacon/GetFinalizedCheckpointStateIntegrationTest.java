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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedCheckpointState;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetFinalizedCheckpointStateIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
  }

  @Test
  public void shouldGetBellatrixStateAsSsz()
      throws IOException, ExecutionException, InterruptedException {
    final Response response =
        getResponse(GetFinalizedCheckpointState.ROUTE, ContentTypes.OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.BELLATRIX.lowerCaseName());
    final Bytes actualResponse = Bytes.wrap(response.body().bytes());
    assertThat(actualResponse)
        .isEqualTo(combinedChainDataClient.getBestState().get().get().sszSerialize());
  }
}
