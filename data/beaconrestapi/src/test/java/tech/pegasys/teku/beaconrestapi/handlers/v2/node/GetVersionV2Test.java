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

package tech.pegasys.teku.beaconrestapi.handlers.v2.node;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class GetVersionV2Test extends AbstractMigratedBeaconHandlerTest {

  private static final ClientVersion BEACON_NODE_VERSION =
      new ClientVersion(
          ClientVersion.TEKU_CLIENT_CODE,
          VersionProvider.CLIENT_IDENTITY,
          VersionProvider.IMPLEMENTATION_VERSION,
          VersionProvider.COMMIT_HASH
              .map(commitHash -> Bytes4.fromHexString(commitHash.substring(0, 8)))
              .orElse(Bytes4.ZERO));

  private static final ClientVersion GETH_VERSION =
      new ClientVersion("geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));

  private static final ClientVersion BESU_VERSION =
      new ClientVersion("besu", "Besu", "24.1.0", Bytes4.fromHexString("0xaabbccdd"));

  private ExecutionClientDataProvider localExecutionClientDataProvider;

  @BeforeEach
  void setUp() {
    localExecutionClientDataProvider = new ExecutionClientDataProvider();
    setHandler(new GetVersionV2(localExecutionClientDataProvider));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final GetVersionV2.VersionDataV2 responseData =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.of(GETH_VERSION));
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);

    final JsonNode resultNode = new ObjectMapper().readTree(data);
    final String referenceJson =
        Resources.toString(
            Resources.getResource(GetVersionV2Test.class, "GetVersionV2Response.json"),
            StandardCharsets.UTF_8);

    final String expectedJson =
        referenceJson
            .replace("<BEACON_NODE_VERSION>", BEACON_NODE_VERSION.version())
            .replace("<BEACON_NODE_COMMIT>", BEACON_NODE_VERSION.commit().toHexString());

    final JsonNode expectedNode = new ObjectMapper().readTree(expectedJson);
    assertThat(resultNode).isEqualTo(expectedNode);
  }

  @Test
  void metadata_shouldHandle200_withoutExecutionClient() throws Exception {
    final GetVersionV2.VersionDataV2 responseData =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.empty());
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);

    final JsonNode resultNode = new ObjectMapper().readTree(data);
    final String referenceJson =
        Resources.toString(
            Resources.getResource(
                GetVersionV2Test.class, "GetVersionV2ResponseNoExecutionClient.json"),
            StandardCharsets.UTF_8);

    final String expectedJson =
        referenceJson
            .replace("<BEACON_NODE_VERSION>", BEACON_NODE_VERSION.version())
            .replace("<BEACON_NODE_COMMIT>", BEACON_NODE_VERSION.commit().toHexString());

    final JsonNode expectedNode = new ObjectMapper().readTree(expectedJson);
    assertThat(resultNode).isEqualTo(expectedNode);
  }

  @Test
  public void shouldReturnBeaconNodeInfoWithoutExecutionClient() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 expectedResponse =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.empty());

    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnExecutionClientInfoWhenAvailable() throws Exception {
    localExecutionClientDataProvider.onExecutionClientVersion(GETH_VERSION);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 expectedResponse =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.of(GETH_VERSION));

    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldClearExecutionClientWhenItBecomesUnavailable() throws Exception {
    localExecutionClientDataProvider.onExecutionClientVersion(BESU_VERSION);
    localExecutionClientDataProvider.onExecutionClientVersionNotAvailable();

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 expectedResponse =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.empty());

    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReflectUpdatedExecutionClientVersion() throws Exception {
    localExecutionClientDataProvider.onExecutionClientVersion(GETH_VERSION);
    final ClientVersion updatedVersion =
        new ClientVersion("geth", "Geth", "1.14.0", Bytes4.fromHexString("0x05060708"));
    localExecutionClientDataProvider.onExecutionClientVersion(updatedVersion);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 expectedResponse =
        new GetVersionV2.VersionDataV2(BEACON_NODE_VERSION, Optional.of(updatedVersion));

    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }
}
