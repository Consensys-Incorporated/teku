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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedBlockRoot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetFinalizedBlockRootIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
  }

  @Test
  void shouldReturnFinalizedBlockRoot() throws IOException {
    createBlocksAtSlots(10);
    chainUpdater.finalizeEpoch(10);
    final Response response = get(UInt64.valueOf(10));
    Assertions.assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldReturnNotFoundWhenBlockIsNotFinalized() throws IOException {
    createBlocksAtSlots(10);
    final Response response = get(UInt64.valueOf(10));
    Assertions.assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  private Response get(final UInt64 slot) throws IOException {
    return getResponse(GetFinalizedBlockRoot.ROUTE.replace("{slot}", slot.toString()));
  }
}
