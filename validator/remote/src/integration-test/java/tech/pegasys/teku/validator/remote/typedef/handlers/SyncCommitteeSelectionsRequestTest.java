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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class SyncCommitteeSelectionsRequestTest extends AbstractTypeDefRequestTestBase {

  private SyncCommitteeSelectionsRequest request;
  private List<SyncCommitteeSelectionProof> entries;

  @BeforeEach
  void setupRequest() {
    request = new SyncCommitteeSelectionsRequest(mockWebServer.url("/"), okHttpClient);
    entries = List.of(createSyncCommitteeSelectionProof());
  }

  @TestTemplate
  public void correctResponseDeserialization() {
    final String mockResponse = readResource("responses/sync_committee_selections.json");
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    // values from sync_committee_selections.json
    final SyncCommitteeSelectionProof expectedSyncCommitteeSelectionProof =
        new SyncCommitteeSelectionProof.Builder()
            .validatorIndex(1)
            .slot(UInt64.ONE)
            .subcommitteeIndex(1)
            .selectionProof(
                "0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505cc411d61252fb6cb3fa0017b679f8bb2305b26a285fa2737f175668d0dff91cc1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505")
            .build();

    final Optional<List<SyncCommitteeSelectionProof>> response = request.submit(entries);
    assertThat(response).isPresent().contains(List.of(expectedSyncCommitteeSelectionProof));
  }

  @TestTemplate
  public void expectedRequest() throws Exception {
    final String mockResponse = readResource("responses/sync_committee_selections.json");
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    request.submit(entries);

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SYNC_COMMITTEE_SELECTIONS.getPath(Collections.emptyMap()));

    final List<SyncCommitteeSelectionProof> entriesSent =
        JsonUtil.parse(
            request.getBody().readUtf8(),
            listOf(SyncCommitteeSelectionProof.SYNC_COMMITTEE_SELECTION_PROOF));
    assertThat(entriesSent).isEqualTo(entries);
  }

  @TestTemplate
  public void handlingBadRequest() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> request.submit(entries)).isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void handlingNotImplemented() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_IMPLEMENTED));

    assertThat(request.submit(entries)).isEmpty();
  }

  @TestTemplate
  public void handlingSyncing() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_SERVICE_UNAVAILABLE));

    assertThat(request.submit(entries)).isEmpty();
  }

  private SyncCommitteeSelectionProof createSyncCommitteeSelectionProof() {
    return new SyncCommitteeSelectionProof.Builder()
        .validatorIndex(dataStructureUtil.randomPositiveInt())
        .slot(dataStructureUtil.randomUInt64())
        .subcommitteeIndex(dataStructureUtil.randomPositiveInt())
        .selectionProof(dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
        .build();
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(entries))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void handle503() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_SERVICE_UNAVAILABLE));
    assertThat(request.submit(entries)).isEmpty();
  }

  @TestTemplate
  void handle501() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_IMPLEMENTED));
    assertThat(request.submit(entries)).isEmpty();
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(entries)).isInstanceOf(IllegalArgumentException.class);
  }
}
