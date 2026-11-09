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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;

public class LivenessTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  public void setup() {
    setHandler(new Liveness(syncDataProvider));
  }

  @Test
  public void shouldReturnOkWhenTekuIsUp() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
  }

  @Test
  public void shouldReturnOkWhenQueryParamAndNoRejectedExecutions() throws Exception {
    rejectedExecutionCount = 0;
    request.setOptionalQueryParameter("failOnRejectedCount", "true");
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
  }

  @Test
  public void shouldReturnServiceNotAvailableWhenQueryParamAndRejectedExecutions()
      throws Exception {
    rejectedExecutionCount = 1;
    request.setOptionalQueryParameter("failOnRejectedCount", "true");
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getCacheLength()).isEqualTo(CacheLength.NO_CACHE);
  }
}
