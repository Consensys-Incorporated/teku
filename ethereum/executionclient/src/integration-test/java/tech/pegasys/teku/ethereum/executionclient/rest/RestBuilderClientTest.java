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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderApiMethod;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

@TestSpecContext(
    milestone = {BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU},
    network = Eth2Network.MAINNET)
class RestBuilderClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Duration WAIT_FOR_CALL_COMPLETION = Duration.ofSeconds(10);

  private static final String INTERNAL_SERVER_ERROR_MESSAGE =
      "{\"code\":500,\"message\":\"Internal server error\"}";

  private static final UInt64 SLOT = UInt64.ONE;

  private static final Bytes32 PARENT_HASH =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");

  private static final BLSPublicKey PUB_KEY =
      BLSPublicKey.fromBytesCompressed(
          Bytes48.fromHexString(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a"));

  private static final Pattern TEKU_USER_AGENT_REGEX = Pattern.compile("teku/v.*");

  private static final Consumer<RecordedRequest> USER_AGENT_HEADER_ASSERTION =
      recordedRequest -> {
        assertThat(recordedRequest.getHeader("User-Agent")).matches(TEKU_USER_AGENT_REGEX);
        assertThat(recordedRequest.getHeader("Accept"))
            .isEqualTo("application/octet-stream;q=1.0,application/json;q=0.9");
      };

  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  private final MockWebServer mockWebServer = new MockWebServer();

  private StubTimeProvider timeProvider;
  private Spec spec;
  private SpecMilestone milestone;
  private OkHttpRestClient okHttpRestClient;
  private SchemaDefinitionsBellatrix schemaDefinitions;

  private RestBuilderClientOptions restBuilderClientOptions;
  private RestBuilderClient restBuilderClient;

  private String signedValidatorRegistrationsRequest;
  private String signedBlindedBeaconBlockRequestJson;
  private SignedBeaconBlock signedBlindedBeaconBlockRequestSsz;

  private String signedBuilderBidResponseJson;
  private String unblindedBuilderPayloadResponseJson;
  private SignedBuilderBid signedBuilderBidResponseSsz;
  private BuilderPayload unblindedBuilderPayloadResponseSsz;

  @BeforeEach
  void setUp(final SpecContext specContext) throws IOException {
    mockWebServer.start();
    timeProvider = StubTimeProvider.withTimeInMillis(UInt64.ONE);

    spec = specContext.getSpec();
    final String endpoint = "http://localhost:" + mockWebServer.getPort();
    okHttpRestClient = new OkHttpRestClient(okHttpClient, endpoint);

    milestone = specContext.getSpecMilestone();

    schemaDefinitions = SchemaDefinitionsBellatrix.required(specContext.getSchemaDefinitions());

    signedValidatorRegistrationsRequest = readResource("builder/signedValidatorRegistrations.json");
    final String milestoneFolder = "builder/" + milestone.toString().toLowerCase(Locale.ROOT);
    signedBlindedBeaconBlockRequestJson =
        readResource(milestoneFolder + "/signedBlindedBeaconBlock.json");
    signedBuilderBidResponseJson = readResource(milestoneFolder + "/signedBuilderBid.json");
    unblindedBuilderPayloadResponseJson =
        readResource(milestoneFolder + "/unblindedBuilderPayload.json");

    signedBlindedBeaconBlockRequestSsz =
        JsonUtil.parse(
            signedBlindedBeaconBlockRequestJson,
            schemaDefinitions.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());

    signedBuilderBidResponseSsz =
        JsonUtil.getAttribute(
                signedBuilderBidResponseJson,
                schemaDefinitions.getSignedBuilderBidSchema().getJsonTypeDefinition(),
                "data")
            .orElseThrow();
    unblindedBuilderPayloadResponseSsz =
        JsonUtil.getAttribute(
                unblindedBuilderPayloadResponseJson,
                schemaDefinitions.getBuilderPayloadSchema().getJsonTypeDefinition(),
                "data")
            .orElseThrow();

    restBuilderClientOptions = RestBuilderClientOptions.DEFAULT;
    restBuilderClient =
        new RestBuilderClient(restBuilderClientOptions, okHttpRestClient, timeProvider, spec, true);
  }

  @AfterEach
  void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  void getStatus_success() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThat(restBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload()).isNull();
            });

    verifyGetRequest("/eth/v1/builder/status");
  }

  @TestTemplate
  void getStatus_failures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest("/eth/v1/builder/status");
  }

  @TestTemplate
  void registerValidators_success() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload()).isNull();
            });

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);
  }

  @TestTemplate
  void registerValidators_zeroRegistrationsDoesNotMakeRequest() {

    final SszList<SignedValidatorRegistration> zeroRegistrations =
        SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getDefault();

    assertThat(restBuilderClient.registerValidators(SLOT, zeroRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload()).isNull();
            });

    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  void registerValidators_failures() {

    final String unknownValidatorError = "{\"code\":400,\"message\":\"unknown validator\"}";

    // Both ssz and json requests will fail
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(unknownValidatorError));
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(unknownValidatorError));

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(unknownValidatorError);
            });

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);
    verifyPostRequestJson("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);

    // Only json will fail (ssz backoff enabled)
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequestJson("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);
  }

  @TestTemplate
  void registerValidators_retryWithJsonAfterSszFailure() {
    final String errorMsg = "{\"code\":415,\"message\":\"unsupported media type\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(415).setBody(errorMsg));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(response -> assertThat(response.isSuccess()).isTrue());

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);
    verifyPostRequestJson("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
  }

  @TestTemplate
  void registerValidators_trySszAfterBackoffTime() {
    final String errorMsg = "{\"code\":400,\"message\":\"bad request\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(errorMsg));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    // First call, will try SSZ and fallback into JSON
    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(response -> assertThat(response.isSuccess()).isTrue());

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);
    verifyPostRequestJson("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);

    // After backoff period, will try SSZ again
    timeProvider.advanceTimeBy(Duration.ofDays(1));
    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(response -> assertThat(response.isSuccess()).isTrue());

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
  }

  @TestTemplate
  void registerValidators_shouldNotFallbackWhenTimingOut() {
    // Creates a RestBuilderClient that has a very tiny timeout
    restBuilderClient =
        new RestBuilderClient(
            tinyTimeoutRestBuilderClientOptions(), okHttpRestClient, timeProvider, spec, true);

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    // set up delayed 200 response
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setHeadersDelay(1, TimeUnit.SECONDS));

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableThat()
        .withCauseInstanceOf(InterruptedIOException.class)
        .withMessageContaining("timeout");

    verifyPostRequestSsz("/eth/v1/builder/validators", signedValidatorRegistrations);
    // Check that we do not fallback into JSON (only 1 request)
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @TestTemplate
  void registerValidators_shouldNotFallbackWhenFailingForNonHttpReasons() {
    okHttpRestClient = spy(okHttpRestClient);
    // Mock asyncPost with ssz to fail
    doReturn(SafeFuture.failedFuture(new ConnectException()))
        .when(okHttpRestClient)
        .postAsync(
            eq(BuilderApiMethod.REGISTER_VALIDATOR.getPath()), any(), any(), eq(true), any());
    restBuilderClient =
        new RestBuilderClient(restBuilderClientOptions, okHttpRestClient, timeProvider, spec, true);

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableThat()
        .withCauseInstanceOf(ConnectException.class);

    // No requests are made (including no fallback to json)
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  void getHeader_success_doesNotSetUserAgentHeader() {

    restBuilderClient =
        new RestBuilderClient(
            restBuilderClientOptions, okHttpRestClient, timeProvider, spec, false);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY,
        recordedRequest ->
            assertThat(recordedRequest.getHeader("User-Agent"))
                .doesNotMatch(TEKU_USER_AGENT_REGEX));
  }

  @TestTemplate
  void getHeader_json_success() {
    runGetHeaderSuccessAsJson();
  }

  @TestTemplate
  void getHeader_ssz_success() {
    runGetHeaderSuccessAsSsz();
  }

  @TestTemplate
  void getHeader_noHeaderAvailable() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload()).isEmpty();
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_failures() {

    final String missingParentHashError =
        "{\"code\":400,\"message\":\"Unknown hash: missing parent hash\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingParentHashError));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(missingParentHashError);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_json_wrongFork(final SpecContext specContext) {
    assumeThat(milestone.isLessThan(FULU)).isTrue();
    specContext.assumeCapellaActive();

    final String milestoneFolder =
        "builder/" + milestone.getPreviousMilestone().toString().toLowerCase(Locale.ROOT);

    signedBuilderBidResponseJson = readResource(milestoneFolder + "/signedBuilderBid.json");

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(MissingRequiredFieldException.class)
        .withMessageMatching(".+required fields: (.+) were not set");

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_ssz_wrongFork(final SpecContext specContext) throws JsonProcessingException {
    assumeThat(milestone.isLessThan(FULU)).isTrue();
    specContext.assumeCapellaActive();

    final String milestoneFolder =
        "builder/" + milestone.getPreviousMilestone().toString().toLowerCase(Locale.ROOT);

    signedBuilderBidResponseJson = readResource(milestoneFolder + "/signedBuilderBid.json");
    signedBuilderBidResponseSsz =
        JsonUtil.getAttribute(
                signedBuilderBidResponseJson,
                spec.forMilestone(milestone.getPreviousMilestone())
                    .getSchemaDefinitions()
                    .toVersionBellatrix()
                    .orElseThrow()
                    .getSignedBuilderBidSchema()
                    .getJsonTypeDefinition(),
                "data")
            .orElseThrow();

    final Buffer responseBodyBuffer = new Buffer();
    responseBodyBuffer.write(signedBuilderBidResponseSsz.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBodyBuffer)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Eth-Consensus-Version", milestone.name().toLowerCase(Locale.ROOT)));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(SszDeserializeException.class);

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_wrongVersion(final SpecContext specContext) {
    specContext.assumeCapellaActive();

    signedBuilderBidResponseJson =
        changeResponseVersion(signedBuilderBidResponseJson, milestone.getPreviousMilestone());

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(IllegalArgumentException.class)
        .withMessageContaining(
            "java.lang.IllegalArgumentException: Wrong response version: expected %s, received %s",
            milestone, milestone.getPreviousMilestone());

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getPayload_success_shouldSwitchBackToJsonIfNextGetHeaderReturnsJson() {
    // as json first
    runGetHeaderSuccessAsJson();
    runGetPayloadSuccessAsJson();

    // then ssz
    runGetHeaderSuccessAsSsz();
    runGetPayloadSuccessAsSsz();

    // than back to json
    runGetHeaderSuccessAsJson();
    runGetPayloadSuccessAsJson();
  }

  @TestTemplate
  void getPayload_failures() {

    String missingSignatureError =
        "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingSignatureError));

    final SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(missingSignatureError);
            });

    verifyPostRequestJson("/eth/v1/builder/blinded_blocks", signedBlindedBeaconBlockRequestJson);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.errorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequestJson("/eth/v1/builder/blinded_blocks", signedBlindedBeaconBlockRequestJson);
  }

  @TestTemplate
  void getPayload_wrongVersion(final SpecContext specContext) {
    specContext.assumeCapellaActive();

    unblindedBuilderPayloadResponseJson =
        changeResponseVersion(
            unblindedBuilderPayloadResponseJson, milestone.getPreviousMilestone());

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(unblindedBuilderPayloadResponseJson));

    final SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              final BuilderPayload builderPayload = response.payload();
              verifyBuilderPayloadResponse(builderPayload);
            });

    verifyPostRequestJson("/eth/v1/builder/blinded_blocks", signedBlindedBeaconBlockRequestJson);
  }

  private void runGetHeaderSuccessAsJson() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponseJson));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
              assertThat(response.receivedAsSsz()).isFalse();
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  private void runGetHeaderSuccessAsSsz() {
    final Buffer responseBodyBuffer = new Buffer();
    responseBodyBuffer.write(signedBuilderBidResponseSsz.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBodyBuffer)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Eth-Consensus-Version", milestone.name().toLowerCase(Locale.ROOT)));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.payload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
              assertThat(response.receivedAsSsz()).isTrue();
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  private void runGetPayloadSuccessAsJson() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(unblindedBuilderPayloadResponseJson));

    final SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    if (milestone.isLessThan(FULU)) {
      assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
          .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
          .satisfies(
              response -> {
                assertThat(response.isSuccess()).isTrue();
                final BuilderPayload builderPayload = response.payload();
                verifyBuilderPayloadResponse(builderPayload);
              });
    } else {
      assertThat(restBuilderClient.getPayloadV2(signedBlindedBeaconBlock))
          .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
          .satisfies(
              response -> {
                assertThat(response.isSuccess()).isTrue();
              });
    }

    final Consumer<RecordedRequest> containsConsensusVersionHeader =
        req -> {
          assertThat(req.getHeader("Eth-Consensus-Version"))
              .isEqualTo(milestone.name().toLowerCase(Locale.ROOT));
          if (milestone.isLessThan(FULU)) {
            assertThat(req.getHeader("Accept"))
                .isEqualTo("application/octet-stream;q=1.0,application/json;q=0.9");
          }
          ;
        };
    final String apiUrl =
        milestone.isLessThan(FULU)
            ? "/eth/v1/builder/blinded_blocks"
            : "/eth/v2/builder/blinded_blocks";
    verifyRequest(
        "POST",
        apiUrl,
        Optional.of(signedBlindedBeaconBlockRequestJson),
        Optional.empty(),
        Optional.of(containsConsensusVersionHeader));
  }

  private void runGetPayloadSuccessAsSsz() {
    final Buffer responseBodyBuffer = new Buffer();
    responseBodyBuffer.write(unblindedBuilderPayloadResponseSsz.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBodyBuffer)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Eth-Consensus-Version", milestone.name().toLowerCase(Locale.ROOT)));

    final SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    if (milestone.isLessThan(FULU)) {
      assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
          .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
          .satisfies(
              response -> {
                assertThat(response.isSuccess()).isTrue();
                final BuilderPayload builderPayload = response.payload();
                verifyBuilderPayloadResponse(builderPayload);
                assertThat(response.receivedAsSsz()).isTrue();
              });
    } else {
      assertThat(restBuilderClient.getPayloadV2(signedBlindedBeaconBlock))
          .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
          .satisfies(
              response -> {
                assertThat(response.isSuccess()).isTrue();
              });
    }
    final Consumer<RecordedRequest> containsConsensusVersionHeader =
        req ->
            assertThat(req.getHeader("Eth-Consensus-Version"))
                .isEqualTo(milestone.name().toLowerCase(Locale.ROOT));
    final String apiUrl =
        milestone.isLessThan(FULU)
            ? "/eth/v1/builder/blinded_blocks"
            : "/eth/v2/builder/blinded_blocks";
    verifyRequest(
        "POST",
        apiUrl,
        Optional.empty(),
        Optional.of(signedBlindedBeaconBlockRequestSsz),
        Optional.of(containsConsensusVersionHeader));
  }

  private void verifyGetRequest(final String apiPath) {
    verifyRequest("GET", apiPath, Optional.empty(), Optional.empty());
  }

  private void verifyGetRequest(
      final String apiPath, final Consumer<RecordedRequest> additionalRequestAssertions) {
    verifyRequest(
        "GET",
        apiPath,
        Optional.empty(),
        Optional.empty(),
        Optional.of(additionalRequestAssertions));
  }

  private void verifyPostRequestJson(final String apiPath, final String requestBody) {
    verifyRequest("POST", apiPath, Optional.of(requestBody), Optional.empty());
  }

  private <T extends SszData> void verifyPostRequestSsz(final String apiPath, final T requestBody) {
    verifyRequest("POST", apiPath, Optional.empty(), Optional.of(requestBody));
  }

  private void verifyRequest(
      final String method,
      final String apiPath,
      final Optional<String> expectedRequestBodyJson,
      final Optional<? extends SszData> expectedRequestBodySsz) {
    verifyRequest(
        method, apiPath, expectedRequestBodyJson, expectedRequestBodySsz, Optional.empty());
  }

  private void verifyRequest(
      final String method,
      final String apiPath,
      final Optional<String> expectedRequestBodyJson,
      final Optional<? extends SszData> expectedRequestBodySsz,
      final Optional<Consumer<RecordedRequest>> additionalRequestAssertions) {
    checkArgument(
        expectedRequestBodyJson.isEmpty() || expectedRequestBodySsz.isEmpty(),
        "At most one of expectedRequestBodyJson and expectedRequestBodySsz should be present");

    try {
      final RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getMethod()).isEqualTo(method);
      assertThat(request.getPath()).isEqualTo(apiPath);
      final Buffer actualRequestBody = request.getBody();

      if (expectedRequestBodyJson.isPresent()) {
        assertThat(actualRequestBody.size()).isNotZero();
        assertThat(OBJECT_MAPPER.readTree(expectedRequestBodyJson.get()))
            .isEqualTo(OBJECT_MAPPER.readTree(actualRequestBody.readUtf8()));
      } else if (expectedRequestBodySsz.isPresent()) {
        assertThat(actualRequestBody.size()).isNotZero();
        assertThat(expectedRequestBodySsz.get().sszSerialize().toArrayUnsafe())
            .isEqualTo(actualRequestBody.readByteArray());
      } else {
        assertThat(actualRequestBody.size()).isZero();
      }

      additionalRequestAssertions.ifPresent(assertions -> assertions.accept(request));
    } catch (final InterruptedException | JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SszList<SignedValidatorRegistration> createSignedValidatorRegistrations() {
    try {
      return JsonUtil.parse(
          signedValidatorRegistrationsRequest,
          SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifySignedBuilderBidResponse(final SignedBuilderBid actual) {
    final DeserializableTypeDefinition<BuilderApiResponse<SignedBuilderBid>>
        responseTypeDefinition =
            BuilderApiResponse.createTypeDefinition(
                schemaDefinitions.getSignedBuilderBidSchema().getJsonTypeDefinition());
    try {
      final SignedBuilderBid expected =
          JsonUtil.parse(signedBuilderBidResponseJson, responseTypeDefinition).data();
      assertThat(actual).isEqualTo(expected);
    } catch (final JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SignedBeaconBlock createSignedBlindedBeaconBlock() {
    try {
      return JsonUtil.parse(
          signedBlindedBeaconBlockRequestJson,
          schemaDefinitions.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
    } catch (final JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifyBuilderPayloadResponse(final BuilderPayload actual) {
    final DeserializableTypeDefinition<? extends BuilderApiResponse<? extends BuilderPayload>>
        responseTypeDefinition =
            BuilderApiResponse.createTypeDefinition(
                schemaDefinitions.getBuilderPayloadSchema().getJsonTypeDefinition());
    try {
      final BuilderPayload expected =
          JsonUtil.parse(unblindedBuilderPayloadResponseJson, responseTypeDefinition).data();
      assertThat(actual).isEqualTo(expected);
    } catch (final JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private static String readResource(final String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String changeResponseVersion(final String json, final SpecMilestone newVersion) {
    return json.replaceFirst(
        "(?<=version\":\\s?\")\\w+", newVersion.toString().toLowerCase(Locale.ROOT));
  }

  private RestBuilderClientOptions tinyTimeoutRestBuilderClientOptions() {
    return new RestBuilderClientOptions(
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        Duration.ofMillis(100));
  }
}
