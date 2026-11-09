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

package tech.pegasys.teku.beaconrestapi.v1.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetForkChoice;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetForkChoiceIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetForkChoiceAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = getResponse(GetForkChoice.ROUTE);
    assertThat(response.code()).isEqualTo(SC_OK);
  }
}
