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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SUBCOMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class CreateSyncCommitteeContributionRequestTest extends AbstractTypeDefRequestTestBase {

  private CreateSyncCommitteeContributionRequest request;
  private UInt64 slot;
  private int subcommitteeIndex;
  private Bytes32 root;

  @BeforeEach
  public void setup() {
    request =
        new CreateSyncCommitteeContributionRequest(mockWebServer.url("/"), okHttpClient, spec);
    slot = dataStructureUtil.randomSlot();
    subcommitteeIndex = dataStructureUtil.randomPositiveInt();
    root = dataStructureUtil.randomBytes32();
  }

  @TestTemplate
  public void createSyncCommitteeContribution_noRequestAtPhase0() {
    assumeThat(specMilestone).isLessThanOrEqualTo(PHASE0);
    request.submit(slot, subcommitteeIndex, root);
    assertThat(mockWebServer.getRequestCount()).isZero();
  }

  @TestTemplate
  public void createSyncCommitteeContribution_makesExpectedRequest() throws Exception {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit(slot, subcommitteeIndex, root);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(
            ValidatorApiMethod.GET_SYNC_COMMITTEE_CONTRIBUTION.getPath(Collections.emptyMap()));
    assertThat(request.getRequestUrl().queryParameter(SLOT)).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter(SUBCOMMITTEE_INDEX))
        .isEqualTo(String.valueOf(subcommitteeIndex));
    assertThat(request.getRequestUrl().queryParameter(BEACON_BLOCK_ROOT))
        .isEqualTo(String.valueOf(root.toHexString()));
  }

  @TestTemplate
  public void shouldGetSyncCommitteeContribution() throws JsonProcessingException {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    final SyncCommitteeContribution response = dataStructureUtil.randomSyncCommitteeContribution();
    final String jsonResponse =
        serialize(
            response,
            spec.getGenesisSchemaDefinitions()
                .toVersionAltair()
                .orElseThrow()
                .getSyncCommitteeContributionSchema()
                .getJsonTypeDefinition());

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(String.format("{\"data\":%s}", jsonResponse)));

    final Optional<SyncCommitteeContribution> maybeSyncCommitteeContribution =
        request.submit(slot, subcommitteeIndex, root);
    assertThat(maybeSyncCommitteeContribution).isPresent();
    assertThat(maybeSyncCommitteeContribution.get()).isEqualTo(response);
  }

  @TestTemplate
  void handle400() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(slot, subcommitteeIndex, root))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle404() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));
    assertThat(request.submit(slot, subcommitteeIndex, root)).isEmpty();
  }

  @TestTemplate
  void handle500() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(slot, subcommitteeIndex, root))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
