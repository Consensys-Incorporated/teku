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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ClientVersionV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {CAPELLA, DENEB, ELECTRA, FULU, GLOAS})
public class OkHttpHttpExecutionEngineClientTest {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  private ObjectMapper objectMapper;
  private DataStructureUtil dataStructureUtil;
  private Spec spec;
  private SpecMilestone specMilestone;

  private ExecutionEngineClient eeClient;

  @BeforeEach
  void setUp(final SpecContext specContext) throws IOException {
    objectMapper = new ObjectMapper();
    dataStructureUtil = specContext.getDataStructureUtil();
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    mockWebServer.start();
    eeClient =
        new OkHttpHttpExecutionEngineClient(
            OkHttpClientCreator.create(DEFAULT_TIMEOUT, LOG, Optional.empty(), timeProvider),
            "http://localhost:" + mockWebServer.getPort(),
            EVENT_LOG,
            timeProvider,
            executionClientEventsPublisher);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  void forkChoiceUpdated_shouldRoundtripWithMockedWebServer() throws Exception {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    final String validationError = "error";
    final PayloadStatus payloadStatusResponse =
        PayloadStatus.invalid(Optional.of(latestValidHash), Optional.of(validationError));

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":"
            + "{\"payloadStatus\" : { \"status\": \"INVALID\", \"latestValidHash\": \""
            + latestValidHash
            + "\", \"validationError\": \""
            + validationError
            + "\"}}}";

    mockSuccessfulResponse(bodyResponse);

    final Bytes32 headBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 safeBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 finalizedBlockHash = dataStructureUtil.randomBytes32();
    final ForkChoiceStateV1 forkChoiceStateV1Request =
        new ForkChoiceStateV1(headBlockHash, safeBlockHash, finalizedBlockHash);

    final PayloadAttributesV1 payloadAttributesV1Request =
        new PayloadAttributesV1(
            UInt64.valueOf(10),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20());

    final SafeFuture<Response<ForkChoiceUpdatedResult>> futureResponseForkChoiceUpdatedResult =
        eeClient.forkChoiceUpdatedV1(
            forkChoiceStateV1Request, Optional.of(payloadAttributesV1Request));

    final RecordedRequest request = mockWebServer.takeRequest();

    final Map<String, Object> data =
        JsonTestUtil.parse(request.getBody().readString(StandardCharsets.UTF_8));

    final Map<String, Object> forkChoiceState =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(0);
    final Map<String, Object> payloadAttributes =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(1);

    verifyJsonRpcMethodCall(data, "engine_forkchoiceUpdatedV1");

    assertThat(forkChoiceState.get("headBlockHash")).isEqualTo(headBlockHash.toHexString());
    assertThat(forkChoiceState.get("safeBlockHash")).isEqualTo(safeBlockHash.toHexString());
    assertThat(forkChoiceState.get("finalizedBlockHash"))
        .isEqualTo(finalizedBlockHash.toHexString());

    assertThat(payloadAttributes.get("timestamp")).isEqualTo("0xa");
    assertThat(payloadAttributes.get("prevRandao"))
        .isEqualTo(payloadAttributesV1Request.prevRandao.toHexString());
    assertThat(payloadAttributes.get("suggestedFeeRecipient"))
        .isEqualTo(payloadAttributesV1Request.suggestedFeeRecipient.toHexString());

    assertThat(futureResponseForkChoiceUpdatedResult)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(
            forkChoiceUpdatedResultResponse ->
                forkChoiceUpdatedResultResponse
                    .payload()
                    .asInternalExecutionPayload()
                    .getPayloadStatus()
                    .equals(payloadStatusResponse));
  }

  @TestTemplate
  public void exchangeCapabilities_shouldBuildRequestAndResponseSuccessfully() throws Exception {
    final List<String> consensusCapabilities = List.of("foo", "bar");
    final List<String> executionCapabilities = List.of("ziz");

    final JsonRpcResponse responseBody = new JsonRpcResponse(executionCapabilities);
    mockSuccessfulResponse(objectMapper.writeValueAsString(responseBody));

    final SafeFuture<Response<List<String>>> response =
        eeClient.exchangeCapabilities(consensusCapabilities);
    assertThat(response)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .isEqualTo(Response.fromPayloadReceivedAsJson(executionCapabilities));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_exchangeCapabilities");
    assertThat(requestData.get("params")).asInstanceOf(LIST).containsExactly(consensusCapabilities);
  }

  @TestTemplate
  public void getClientVersionV1_shouldBuildRequestAndResponseSuccessfully() throws Exception {
    final ClientVersionV1 consensusClientVersion =
        new ClientVersionV1("TK", "teku", "1.0.0", Bytes4.fromHexString("87fa8ca7"));
    final ClientVersionV1 executionClientVersion =
        new ClientVersionV1("BU", "besu", "1.0.0", Bytes4.fromHexString("8dba2981"));

    final JsonRpcResponse responseBody = new JsonRpcResponse(List.of(executionClientVersion));
    mockSuccessfulResponse(objectMapper.writeValueAsString(responseBody));

    final SafeFuture<Response<List<ClientVersionV1>>> response =
        eeClient.getClientVersionV1(consensusClientVersion);
    assertThat(response)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .isEqualTo(Response.fromPayloadReceivedAsJson(List.of(executionClientVersion)));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_getClientVersionV1");
    assertThat(requestData.get("params"))
        .asInstanceOf(LIST)
        .hasSize(1)
        .first()
        .asInstanceOf(MAP)
        .hasSize(4)
        .containsEntry("code", "TK")
        .containsEntry("name", "teku")
        .containsEntry("version", "1.0.0")
        .containsEntry("commit", "0x87fa8ca7");
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  public void newPayloadV3_shouldBuildRequestAndResponseSuccessfully() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    final PayloadStatus payloadStatusResponse =
        PayloadStatus.valid(Optional.of(latestValidHash), Optional.empty());

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":"
            + "{ \"status\": \"VALID\", \"latestValidHash\": \""
            + latestValidHash
            + "\", \"validationError\": null}}";

    mockSuccessfulResponse(bodyResponse);

    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV3 executionPayloadV3 =
        ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);

    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();

    final SafeFuture<Response<PayloadStatusV1>> futureResponse =
        eeClient.newPayloadV3(executionPayloadV3, blobVersionedHashes, parentBeaconBlockRoot);

    assertThat(futureResponse)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(
            response ->
                response.payload().asInternalExecutionPayload().equals(payloadStatusResponse));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_newPayloadV3");

    final Map<String, Object> executionPayloadV3Parameter =
        (Map<String, Object>) ((List<Object>) requestData.get("params")).get(0);
    // 17 fields in ExecutionPayloadV3
    assertThat(executionPayloadV3Parameter).hasSize(17);
    // sanity check
    assertThat(executionPayloadV3Parameter.get("parentHash"))
        .isEqualTo(executionPayloadV3.parentHash.toHexString());

    assertThat(executionPayloadV3Parameter.get("blobGasUsed"))
        .isEqualTo(
            Bytes.ofUnsignedLong(executionPayloadV3.blobGasUsed.longValue()).toQuantityHexString());
    assertThat(executionPayloadV3Parameter.get("excessBlobGas"))
        .isEqualTo(
            Bytes.ofUnsignedLong(executionPayloadV3.excessBlobGas.longValue())
                .toQuantityHexString());
    assertThat(((List<Object>) requestData.get("params")).get(1))
        .asInstanceOf(LIST)
        .containsExactlyElementsOf(
            blobVersionedHashes.stream()
                .map(VersionedHash::toHexString)
                .collect(Collectors.toList()));
    assertThat(((List<Object>) requestData.get("params")).get(2))
        .asString()
        .isEqualTo(parentBeaconBlockRoot.toHexString());
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  public void newPayloadV4_shouldBuildRequestAndResponseSuccessfully() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    final PayloadStatus payloadStatusResponse =
        PayloadStatus.valid(Optional.of(latestValidHash), Optional.empty());

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":"
            + "{ \"status\": \"VALID\", \"latestValidHash\": \""
            + latestValidHash
            + "\", \"validationError\": null}}";

    mockSuccessfulResponse(bodyResponse);

    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV3 executionPayloadV3 =
        ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);

    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<Bytes> executionRequests = dataStructureUtil.randomEncodedExecutionRequests();

    final SafeFuture<Response<PayloadStatusV1>> futureResponse =
        eeClient.newPayloadV4(
            executionPayloadV3, blobVersionedHashes, parentBeaconBlockRoot, executionRequests);

    assertThat(futureResponse)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(
            response ->
                response.payload().asInternalExecutionPayload().equals(payloadStatusResponse));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_newPayloadV4");

    final Map<String, Object> executionPayloadV3Parameter =
        (Map<String, Object>) ((List<Object>) requestData.get("params")).get(0);
    // 17 fields in ExecutionPayloadV4
    assertThat(executionPayloadV3Parameter).hasSize(17);
    // sanity check
    assertThat(executionPayloadV3Parameter.get("parentHash"))
        .isEqualTo(executionPayloadV3.parentHash.toHexString());

    assertThat(((List<Object>) requestData.get("params")).get(1))
        .asInstanceOf(LIST)
        .containsExactlyElementsOf(
            blobVersionedHashes.stream()
                .map(VersionedHash::toHexString)
                .collect(Collectors.toList()));
    assertThat(((List<Object>) requestData.get("params")).get(2))
        .asString()
        .isEqualTo(parentBeaconBlockRoot.toHexString());
    assertThat(((List<Object>) requestData.get("params")).get(3))
        .asInstanceOf(LIST)
        .containsExactlyElementsOf(
            executionRequests.stream().map(Bytes::toHexString).collect(Collectors.toList()));
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  public void getBlobsV1_shouldBuildRequestAndResponseSuccessfully() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecars(
            spec.getMaxBlobsPerBlockForHighestMilestone().orElseThrow());
    final List<BlobAndProofV1> blobsAndProofsV1 =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndProofV1(
                        blobSidecar.getBlob().getBytes(),
                        blobSidecar.getKZGProof().getBytesCompressed()))
            .toList();
    final String blobsAndProofsJson =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    String.format(
                        "{ \"blob\": \"%s\", \"proof\": \"%s\" }",
                        blobSidecar.getBlob().getBytes().toHexString(),
                        blobSidecar.getKZGProof().getBytesCompressed().toHexString()))
            .collect(Collectors.joining(", "));
    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\": [" + blobsAndProofsJson + "]}";

    mockSuccessfulResponse(bodyResponse);

    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(3);

    final SafeFuture<Response<List<BlobAndProofV1>>> futureResponse =
        eeClient.getBlobsV1(blobVersionedHashes);

    assertThat(futureResponse)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(response -> response.payload().equals(blobsAndProofsV1));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_getBlobsV1");
    assertThat(requestData.get("params"))
        .asInstanceOf(LIST)
        .containsExactly(blobVersionedHashes.stream().map(VersionedHash::toHexString).toList());
  }

  @TestTemplate
  public void getBlobsV2_shouldBuildRequestAndResponseSuccessfully() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    assertThat(blobsBundle.getBlobs()).isNotEmpty();

    final List<BlobAndProofV2> blobsAndProofsV2 =
        IntStream.range(0, blobsBundle.getBlobs().size())
            .mapToObj(
                blobIndex ->
                    new BlobAndProofV2(
                        blobsBundle.getBlobs().get(blobIndex).getBytes(),
                        blobsBundle.getProofs().stream()
                            .map(KZGProof::getBytesCompressed)
                            .skip((long) blobIndex * CELLS_PER_EXT_BLOB)
                            .limit(CELLS_PER_EXT_BLOB)
                            .toList()))
            .toList();
    final String blobsAndProofsV2Json = objectMapper.writeValueAsString(blobsAndProofsV2);

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":" + blobsAndProofsV2Json + "}";

    mockSuccessfulResponse(bodyResponse);

    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(2);

    final SafeFuture<Response<List<BlobAndProofV2>>> futureResponse =
        eeClient.getBlobsV2(blobVersionedHashes);

    assertThat(futureResponse)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(response -> response.payload().equals(blobsAndProofsV2));

    final Map<String, Object> requestData = takeRequest();
    verifyJsonRpcMethodCall(requestData, "engine_getBlobsV2");
    assertThat(requestData.get("params"))
        .asInstanceOf(LIST)
        .containsExactly(blobVersionedHashes.stream().map(VersionedHash::toHexString).toList());
  }

  private void mockSuccessfulResponse(final String responseBody) {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));
  }

  private Map<String, Object> takeRequest() {
    try {
      final RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
      return JsonTestUtil.parse(request.getBody().readString(StandardCharsets.UTF_8));
    } catch (final Exception e) {
      fail("Error taking request from mock server", e);
      throw new RuntimeException(e);
    }
  }

  private void verifyJsonRpcMethodCall(final Map<String, Object> data, final String method) {
    assertThat(data.get("method")).asInstanceOf(STRING).isEqualTo(method);
    assertThat(data.get("id")).asInstanceOf(INTEGER).isGreaterThanOrEqualTo(0);
    assertThat(data.get("jsonrpc")).asInstanceOf(STRING).isEqualTo("2.0");
  }

  public static class JsonRpcResponse {

    private final String jsonrpc = "2.0";
    private final String id = "0";
    private final Object result;

    public JsonRpcResponse(final Object result) {
      this.result = result;
    }

    @JsonProperty
    public String getJsonrpc() {
      return jsonrpc;
    }

    @JsonProperty
    public String getId() {
      return id;
    }

    @JsonProperty
    public Object getResult() {
      return result;
    }
  }
}
