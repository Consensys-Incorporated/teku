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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderApiMethod;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient.ResponseSchemaAndDeserializableTypeDefinition;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class RestBuilderClient implements BuilderClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final Map.Entry<String, String> USER_AGENT_HEADER =
      Map.entry(
          "User-Agent",
          VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.IMPLEMENTATION_VERSION);

  private static final Map.Entry<String, String> ACCEPT_HEADER =
      Map.entry("Accept", "application/octet-stream;q=1.0,application/json;q=0.9");

  private static final AtomicBoolean LAST_RECEIVED_HEADER_WAS_IN_SSZ = new AtomicBoolean(false);

  private final Map<
          SpecMilestone, ResponseSchemaAndDeserializableTypeDefinition<? extends BuilderPayload>>
      cachedBuilderApiPayloadResponseType = new ConcurrentHashMap<>();

  private final Map<SpecMilestone, ResponseSchemaAndDeserializableTypeDefinition<SignedBuilderBid>>
      cachedBuilderApiSignedBuilderBidResponseType = new ConcurrentHashMap<>();

  public static final UInt64 REGISTER_VALIDATOR_SSZ_BACKOFF_TIME_MILLIS =
      UInt64.valueOf(TimeUnit.DAYS.toMillis(1));

  private final RestBuilderClientOptions options;
  private final RestClient restClient;
  private final TimeProvider timeProvider;
  private final Spec spec;
  private final boolean setUserAgentHeader;

  private UInt64 nextSszRegisterValidatorsTryMillis = UInt64.ZERO;

  public RestBuilderClient(
      final RestBuilderClientOptions options,
      final RestClient restClient,
      final TimeProvider timeProvider,
      final Spec spec,
      final boolean setUserAgentHeader) {
    this.options = options;
    this.restClient = restClient;
    this.timeProvider = timeProvider;
    this.spec = spec;
    this.setUserAgentHeader = setUserAgentHeader;
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return restClient.getAsync(
        BuilderApiMethod.GET_STATUS.getPath(), buildHeadersWith(), options.builderStatusTimeout());
  }

  @Override
  public SafeFuture<Response<Void>> registerValidators(
      final UInt64 slot, final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {

    if (signedValidatorRegistrations.isEmpty()) {
      return SafeFuture.completedFuture(Response.fromNullPayload());
    }

    if (nextSszRegisterValidatorsTryMillis.isGreaterThan(timeProvider.getTimeInMillis())) {
      return registerValidatorsUsingJson(signedValidatorRegistrations);
    }

    return registerValidatorsUsingSsz(signedValidatorRegistrations)
        .thenCompose(
            response -> {
              // Any failure will trigger a retry, not only Unsupported Media Type (415)
              if (response.isFailure()) {
                LOG.debug(
                    "Failed to register validator using SSZ. Will retry using JSON (error: {})",
                    response.errorMessage());

                nextSszRegisterValidatorsTryMillis =
                    timeProvider.getTimeInMillis().plus(REGISTER_VALIDATOR_SSZ_BACKOFF_TIME_MILLIS);

                return registerValidatorsUsingJson(signedValidatorRegistrations);
              }

              return SafeFuture.completedFuture(response);
            });
  }

  private SafeFuture<Response<Void>> registerValidatorsUsingJson(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {
    return restClient.postAsync(
        BuilderApiMethod.REGISTER_VALIDATOR.getPath(),
        buildHeadersWith(),
        signedValidatorRegistrations,
        false,
        options.builderRegisterValidatorTimeout());
  }

  private SafeFuture<Response<Void>> registerValidatorsUsingSsz(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {
    return restClient.postAsync(
        BuilderApiMethod.REGISTER_VALIDATOR.getPath(),
        buildHeadersWith(),
        signedValidatorRegistrations,
        true,
        options.builderRegisterValidatorTimeout());
  }

  @Override
  public SafeFuture<Response<Optional<SignedBuilderBid>>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {

    final Map<String, String> urlParams = new HashMap<>();
    urlParams.put("slot", slot.toString());
    urlParams.put("parent_hash", parentHash.toHexString());
    urlParams.put("pubkey", pubKey.toBytesCompressed().toHexString());

    final SpecVersion specVersion = spec.atSlot(slot);
    final SpecMilestone milestone = specVersion.getMilestone();

    final ResponseSchemaAndDeserializableTypeDefinition<SignedBuilderBid> responseTypeDefinition =
        cachedBuilderApiSignedBuilderBidResponseType.computeIfAbsent(
            milestone,
            __ -> {
              final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
                  getSchemaDefinitionsBellatrix(specVersion);
              final SignedBuilderBidSchema signedBuilderBidSchema =
                  schemaDefinitionsBellatrix.getSignedBuilderBidSchema();

              return new ResponseSchemaAndDeserializableTypeDefinition<>(
                  signedBuilderBidSchema,
                  BuilderApiResponse.createTypeDefinition(
                      signedBuilderBidSchema.getJsonTypeDefinition()));
            });

    return restClient
        .getAsync(
            BuilderApiMethod.GET_HEADER.resolvePath(urlParams),
            buildHeadersWith(ACCEPT_HEADER),
            responseTypeDefinition,
            options.builderProposalDelayTolerance())
        .thenApply(
            response ->
                response.unwrapVersioned(
                    this::extractSignedBuilderBid, milestone, BuilderApiResponse::version, true))
        .thenApply(Response::convertToOptional)
        .thenPeek(response -> LAST_RECEIVED_HEADER_WAS_IN_SSZ.set(response.receivedAsSsz()));
  }

  @Override
  public SafeFuture<Response<BuilderPayload>> getPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {

    final UInt64 blockSlot = signedBlindedBeaconBlock.getSlot();
    final SpecVersion specVersion = spec.atSlot(blockSlot);
    final SpecMilestone milestone = specVersion.getMilestone();
    final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
        getSchemaDefinitionsBellatrix(specVersion);

    final ResponseSchemaAndDeserializableTypeDefinition<? extends BuilderPayload>
        responseTypeDefinition =
            cachedBuilderApiPayloadResponseType.computeIfAbsent(
                milestone,
                __ -> payloadTypeDefinition(schemaDefinitionsBellatrix.getBuilderPayloadSchema()));

    return restClient
        .postAsync(
            BuilderApiMethod.GET_PAYLOAD.getPath(),
            buildHeadersWith(
                ACCEPT_HEADER,
                Map.entry(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT))),
            signedBlindedBeaconBlock,
            LAST_RECEIVED_HEADER_WAS_IN_SSZ.get(),
            responseTypeDefinition,
            options.builderGetPayloadTimeout())
        .thenApply(
            response ->
                response.unwrapVersioned(
                    this::extractBuilderPayload, milestone, BuilderApiResponse::version, false));
  }

  @Override
  public SafeFuture<Response<Void>> getPayloadV2(final SignedBeaconBlock signedBlindedBeaconBlock) {
    final UInt64 blockSlot = signedBlindedBeaconBlock.getSlot();
    final SpecVersion specVersion = spec.atSlot(blockSlot);
    final SpecMilestone milestone = specVersion.getMilestone();

    return restClient.postAsync(
        BuilderApiMethod.GET_PAYLOAD_V2.getPath(),
        buildHeadersWith(
            Map.entry(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT))),
        signedBlindedBeaconBlock,
        LAST_RECEIVED_HEADER_WAS_IN_SSZ.get(),
        options.builderGetPayloadTimeout());
  }

  @SafeVarargs
  public final Map<String, String> buildHeadersWith(final Map.Entry<String, String>... headers) {
    final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    if (setUserAgentHeader) {
      builder.put(USER_AGENT_HEADER);
    }
    Arrays.stream(headers).forEach(builder::put);

    return builder.build();
  }

  private <T extends BuilderPayload>
      ResponseSchemaAndDeserializableTypeDefinition<T> payloadTypeDefinition(
          final BuilderPayloadSchema<T> schema) {
    final DeserializableTypeDefinition<T> typeDefinition = schema.getJsonTypeDefinition();
    return new ResponseSchemaAndDeserializableTypeDefinition<>(
        schema, BuilderApiResponse.createTypeDefinition(typeDefinition));
  }

  private <T extends SignedBuilderBid> SignedBuilderBid extractSignedBuilderBid(
      final BuilderApiResponse<T> builderApiResponse) {
    return builderApiResponse.data();
  }

  private <T extends BuilderPayload> BuilderPayload extractBuilderPayload(
      final BuilderApiResponse<T> builderApiResponse) {
    return builderApiResponse.data();
  }

  private SchemaDefinitionsBellatrix getSchemaDefinitionsBellatrix(final SpecVersion specVersion) {
    return specVersion
        .getSchemaDefinitions()
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    specVersion.getMilestone()
                        + " is not a supported milestone for the builder rest api. Milestones >= Bellatrix are supported."));
  }
}
