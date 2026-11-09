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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;

public class AsyncApiResponseTest {
  private static final String BODY = "{\"slot\":\"1\"}";

  @Test
  void shouldGetCode() {
    final AsyncApiResponse response = AsyncApiResponse.respondWithCode(SC_OK);
    assertThat(response.getResponseCode()).isEqualTo(SC_OK);
    assertThat(response.getResponseBody()).isEmpty();
  }

  @Test
  void shouldRespondWithError() {
    final AsyncApiResponse response =
        AsyncApiResponse.respondWithError(SC_BAD_REQUEST, "bad request");
    assertThat(response.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.getResponseBody().orElseThrow())
        .isEqualTo(new HttpErrorResponse(SC_BAD_REQUEST, "bad request"));
  }

  @Test
  void shouldRespondOk() {
    final AsyncApiResponse response = AsyncApiResponse.respondOk(BODY);
    assertThat(response.getResponseCode()).isEqualTo(SC_OK);
    assertThat(response.getResponseBody().orElseThrow()).isEqualTo(BODY);
  }
}
