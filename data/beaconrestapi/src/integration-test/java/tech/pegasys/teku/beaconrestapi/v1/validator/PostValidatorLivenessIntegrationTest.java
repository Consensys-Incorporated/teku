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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostValidatorLiveness;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class PostValidatorLivenessIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    checkEmptyBodyToRoute(PostValidatorLiveness.ROUTE, SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnUnavailableWhenChainDataNotAvailable() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    checkEmptyBodyToRoute(PostValidatorLiveness.ROUTE, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldDetectActiveValidator() throws IOException {
    final UInt64 epoch = UInt64.ZERO;
    final UInt64 validatorIndex = UInt64.ONE;
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    blockReceivedFromProposer(spec.computeStartSlotAtEpoch(epoch).plus(1), validatorIndex);
    setCurrentSlot(12);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final Response response =
        post(getValidatorLivenessUrl(epoch), String.format("[\"%s\"]", validatorIndex));
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);
    assertThat(data.get(0).get("index").asInt()).isEqualTo(validatorIndex.intValue());
    assertThat(data.get(0).get("is_live").asBoolean()).isTrue();
    assertThat(data.size()).isEqualTo(1);
  }

  private String getValidatorLivenessUrl(final UInt64 epoch) {
    return PostValidatorLiveness.ROUTE.replace("{epoch}", epoch.toString());
  }

  private void blockReceivedFromProposer(final UInt64 slot, final UInt64 proposerIndex) {
    // prime the cache for validator, would be the same as seeing a block come in.
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    when(block.getSlot()).thenReturn(slot);
    when(block.getProposerIndex()).thenReturn(proposerIndex);

    activeValidatorChannel.onBlockImported(block);
  }
}
