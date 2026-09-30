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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

@TestSpecContext(milestone = SpecMilestone.HEZE, network = Eth2Network.MINIMAL)
class InclusionListRequestsTest extends AbstractTypeDefRequestTestBase {
  private final Bytes32 dependentRoot = Bytes32.fromHexStringLenient("0x1234");
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private RemoteValidatorApiHandler apiHandler;

  @BeforeEach
  void setUp() {
    apiHandler =
        new RemoteValidatorApiHandler(
            mockWebServer.url("/"),
            spec,
            new OkHttpValidatorTypeDefClient(
                okHttpClient, mockWebServer.url("/"), spec, false, false),
            asyncRunner,
            asyncRunner,
            true);
  }

  @TestTemplate
  void shouldRequestDutiesWithoutCommitteeRoot() throws Exception {
    mockWebServer.enqueue(new MockResponse().setBody(dutiesResponse()));

    final SafeFuture<Optional<InclusionListDuties>> result =
        apiHandler.getInclusionListDuties(UInt64.ONE, IntList.of(3));
    asyncRunner.executeQueuedActions();

    final InclusionListDuties duties = safeJoin(result).orElseThrow();
    assertThat(duties.dependentRoot()).isEqualTo(dependentRoot);
    assertThat(duties.duties()).hasSize(1);
    assertThat(duties.duties().getFirst().validatorIndex()).isEqualTo(UInt64.valueOf(3));
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/eth/v1/validator/duties/inclusion_list/1");
    assertThat(request.getBody().readUtf8()).isEqualTo("[\"3\"]");
  }

  @TestTemplate
  void shouldConstructInclusionListFromDutiesAndTransactions() throws Exception {
    mockWebServer.enqueue(new MockResponse().setBody(dutiesResponse()));
    mockWebServer.enqueue(new MockResponse().setBody("{\"data\":[\"0x1234\"]}"));

    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SafeFuture<Optional<InclusionList>> result =
        apiHandler.createInclusionList(slot, UInt64.valueOf(3));
    asyncRunner.executeQueuedActions();

    final InclusionList inclusionList = safeJoin(result).orElseThrow();
    assertThat(inclusionList.getSlot()).isEqualTo(slot);
    assertThat(inclusionList.getValidatorIndex()).isEqualTo(UInt64.valueOf(3));
    assertThat(inclusionList.getDependentRoot()).isEqualTo(dependentRoot);
    assertThat(inclusionList.getTransactions().get(0).getBytes().toHexString()).isEqualTo("0x1234");
    assertThat(mockWebServer.takeRequest().getPath())
        .isEqualTo("/eth/v1/validator/duties/inclusion_list/1");
    final RecordedRequest transactionRequest = mockWebServer.takeRequest();
    assertThat(transactionRequest.getMethod()).isEqualTo("GET");
    assertThat(transactionRequest.getPath())
        .isEqualTo("/eth/v1/validator/inclusion_list?slot=" + slot);
  }

  @TestTemplate
  void shouldNotConstructInclusionListWhenTransactionsUnavailable() {
    mockWebServer.enqueue(new MockResponse().setBody(dutiesResponse()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(503));

    final SafeFuture<Optional<InclusionList>> result =
        apiHandler.createInclusionList(spec.computeStartSlotAtEpoch(UInt64.ONE), UInt64.valueOf(3));
    asyncRunner.executeQueuedActions();

    assertThat(safeJoin(result)).isEmpty();
  }

  @TestTemplate
  void shouldSubmitListsIndividuallyAndReportOriginalFailureIndex() throws Exception {
    final List<SignedInclusionList> signedInclusionLists =
        List.of(
            dataStructureUtil.randomSignedInclusionList(),
            dataStructureUtil.randomSignedInclusionList());
    mockWebServer.enqueue(new MockResponse());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .setBody("{\"code\":400,\"message\":\"Invalid inclusion list\"}"));

    final SafeFuture<List<SubmitDataError>> result =
        apiHandler.sendSignedInclusionLists(signedInclusionLists);
    asyncRunner.executeQueuedActions();

    assertThat(safeJoin(result))
        .containsExactly(new SubmitDataError(UInt64.ONE, "Invalid inclusion list"));
    for (final SignedInclusionList signedInclusionList : signedInclusionLists) {
      final RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getMethod()).isEqualTo("POST");
      assertThat(request.getPath()).isEqualTo("/eth/v1/validator/inclusion_list");
      assertThat(request.getHeader("Eth-Consensus-Version")).isEqualTo("heze");
      assertThat(OBJECT_MAPPER.readTree(request.getBody().readUtf8()).get("data"))
          .isEqualTo(
              OBJECT_MAPPER.readTree(
                  JsonUtil.serialize(
                      signedInclusionList,
                      signedInclusionList.getSchema().getJsonTypeDefinition())));
    }
  }

  private String dutiesResponse() {
    return """
        {"dependent_root":"%s","execution_optimistic":false,
         "data":[{"pubkey":"%s","validator_index":"3","slot":"%s"}]}
        """
        .formatted(
            dependentRoot,
            dataStructureUtil.randomPublicKey(),
            spec.computeStartSlotAtEpoch(UInt64.ONE));
  }
}
