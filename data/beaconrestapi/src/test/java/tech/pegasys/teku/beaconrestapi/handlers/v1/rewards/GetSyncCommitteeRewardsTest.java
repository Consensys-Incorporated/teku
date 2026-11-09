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

package tech.pegasys.teku.beaconrestapi.handlers.v1.rewards;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class GetSyncCommitteeRewardsTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private final SyncCommitteeRewardData responseData = new SyncCommitteeRewardData(false, false);

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalAltair());
    initialise(SpecMilestone.ALTAIR);
    genesis();
    final SyncAggregate syncAggregate =
        dataStructureUtil.randomSyncAggregate(0, 3, 4, 7, 8, 9, 10, 16, 17, 20, 23, 25, 26, 29, 30);
    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create().setSyncAggregate(syncAggregate);
    SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(1, blockOptions);
    chainUpdater.updateBestBlock(blockAndState);
    setHandler(new GetSyncCommitteeRewards(chainDataProvider));
    request.setPathParameter("block_id", "head");
  }

  @Test
  void shouldReturnSyncCommitteeRewardsInformation_emptyValidators() throws Exception {
    request.setRequestBody(List.of());
    handler.handleRequest(request);

    final SyncCommitteeRewardData output =
        chainDataProvider.getSyncCommitteeRewardsFromBlockId("head", Set.of()).get().orElseThrow();
    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(output);
  }

  @Test
  void shouldReturnSyncCommitteeRewardsInformation_specifiedValidators() throws Exception {
    request.setRequestBody(List.of("0", "3", "9", "10"));
    handler.handleRequest(request);

    final SyncCommitteeRewardData output =
        chainDataProvider
            .getSyncCommitteeRewardsFromBlockId("head", Set.of("0", "3", "9", "10"))
            .get()
            .orElseThrow();
    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(output);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    responseData.increaseReward(1, 1029L);
    responseData.decreaseReward(2, 3920L);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetSyncCommitteeRewardsTest.class, "getSyncCommitteeRewards.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
