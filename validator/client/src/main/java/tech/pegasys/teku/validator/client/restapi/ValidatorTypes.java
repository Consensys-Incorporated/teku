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

package tech.pegasys.teku.validator.client.restapi;

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.PUBKEY_API_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PUBKEY;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.enumOf;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.MAX_BUILDER_AUTH_DATA_SIZE;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.MAX_BUILDER_URL_SIZE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteRemoteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostRemoteKeysRequest;

public class ValidatorTypes {

  public static final SerializableTypeDefinition<PostKeyResult> POST_KEY_RESULT =
      SerializableTypeDefinition.<PostKeyResult>object()
          .name("PostKeyResult")
          .withField("status", enumOf(ImportStatus.class), PostKeyResult::getImportStatus)
          .withOptionalField("message", STRING_TYPE, PostKeyResult::getMessage)
          .build();

  public static final SerializableTypeDefinition<List<PostKeyResult>> POST_KEYS_RESPONSE =
      SerializableTypeDefinition.<List<PostKeyResult>>object()
          .name("PostKeysResponse")
          .withField("data", listOf(POST_KEY_RESULT), Function.identity())
          .build();

  public static final DeserializableTypeDefinition<PostKeysRequest> POST_KEYS_REQUEST =
      DeserializableTypeDefinition.object(PostKeysRequest.class)
          .name("PostKeysRequest")
          .initializer(PostKeysRequest::new)
          .withField(
              "keystores",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              PostKeysRequest::getKeystores,
              PostKeysRequest::setKeystores)
          .withField(
              "passwords",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              PostKeysRequest::getPasswords,
              PostKeysRequest::setPasswords)
          .withOptionalField(
              "slashing_protection",
              CoreTypes.string(
                  "JSON serialized representation of the slash protection data in format defined in EIP-3076: Slashing Protection Interchange Format."),
              PostKeysRequest::getSlashingProtection,
              PostKeysRequest::setSlashingProtection)
          .build();

  public static final SerializableTypeDefinition<Validator> OWNED_VALIDATOR =
      SerializableTypeDefinition.object(Validator.class)
          .withField("validating_pubkey", PUBKEY_API_TYPE, Validator::getPublicKey)
          .withOptionalField(
              "derivation_path",
              CoreTypes.string("The derivation path (if present in the imported keystore)."),
              __ -> Optional.empty())
          .withField(
              "readonly",
              BOOLEAN_TYPE.withDescription("Whether the validator can be modified"),
              Validator::isReadOnly)
          .build();

  public static final SerializableTypeDefinition<List<Validator>> LIST_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Validator>>object()
          .name("ListKeysResponse")
          .withField("data", listOf(OWNED_VALIDATOR), Function.identity())
          .build();

  public static final DeserializableTypeDefinition<URL> URL_TYPE =
      DeserializableTypeDefinition.string(URL.class)
          .name("Signer")
          .description("URL of the remote signer to use for this validator")
          .formatter(URL::toString)
          .parser(
              url -> {
                try {
                  return URI.create(url).toURL();
                } catch (MalformedURLException e) {
                  throw new IllegalArgumentException(e);
                }
              })
          .build();

  public static final DeserializableTypeDefinition<ExternalValidator> EXTERNAL_VALIDATOR_STORE =
      DeserializableTypeDefinition.object(ExternalValidator.class)
          .name("ExternalValidatorStore")
          .initializer(ExternalValidator::new)
          .withField(
              "pubkey",
              PUBKEY_API_TYPE,
              ExternalValidator::getPublicKey,
              ExternalValidator::setPublicKey)
          .withOptionalField("url", URL_TYPE, ExternalValidator::getUrl, ExternalValidator::setUrl)
          .build();

  public static final DeserializableTypeDefinition<ExternalValidator>
      EXTERNAL_VALIDATOR_RESPONSE_TYPE =
          DeserializableTypeDefinition.object(ExternalValidator.class)
              .name("SignerDefinition")
              .initializer(ExternalValidator::new)
              .withField(
                  "pubkey",
                  PUBKEY_API_TYPE,
                  ExternalValidator::getPublicKey,
                  ExternalValidator::setPublicKey)
              .withOptionalField(
                  "url", URL_TYPE, ExternalValidator::getUrl, ExternalValidator::setUrl)
              .withField(
                  "readonly",
                  BOOLEAN_TYPE.withDescription("Whether the validator can be modified"),
                  ExternalValidator::isReadOnly,
                  ExternalValidator::setReadOnly)
              .build();

  public static final SerializableTypeDefinition<List<ExternalValidator>>
      LIST_REMOTE_KEYS_RESPONSE_TYPE =
          SerializableTypeDefinition.<List<ExternalValidator>>object()
              .name("ListRemoteKeysResponse")
              .withField("data", listOf(EXTERNAL_VALIDATOR_RESPONSE_TYPE), Function.identity())
              .build();

  public static final DeserializableTypeDefinition<ExternalValidator>
      EXTERNAL_VALIDATOR_REQUEST_TYPE =
          DeserializableTypeDefinition.object(ExternalValidator.class)
              .name("ImportRemoteSignerDefinition")
              .initializer(ExternalValidator::new)
              .withField(
                  "pubkey",
                  PUBKEY_API_TYPE,
                  ExternalValidator::getPublicKey,
                  ExternalValidator::setPublicKey)
              .withOptionalField(
                  "url", URL_TYPE, ExternalValidator::getUrl, ExternalValidator::setUrl)
              .build();

  public static final DeserializableTypeDefinition<PostRemoteKeysRequest> POST_REMOTE_KEYS_REQUEST =
      DeserializableTypeDefinition.object(PostRemoteKeysRequest.class)
          .name("PostRemoteKeysRequest")
          .initializer(PostRemoteKeysRequest::new)
          .withField(
              "remote_keys",
              DeserializableTypeDefinition.listOf(EXTERNAL_VALIDATOR_REQUEST_TYPE),
              PostRemoteKeysRequest::getExternalValidators,
              PostRemoteKeysRequest::setExternalValidators)
          .build();

  public static final SerializableTypeDefinition<DeleteKeyResult> DELETE_KEY_RESULT =
      SerializableTypeDefinition.object(DeleteKeyResult.class)
          .name("DeleteKeyResult")
          .withField("status", enumOf(DeletionStatus.class), DeleteKeyResult::getStatus)
          .withOptionalField("message", STRING_TYPE, DeleteKeyResult::getMessage)
          .build();

  public static final SerializableTypeDefinition<DeleteKeysResponse> DELETE_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.object(DeleteKeysResponse.class)
          .name("DeleteKeysResponse")
          .withField("data", listOf(DELETE_KEY_RESULT), DeleteKeysResponse::getData)
          .withField("slashing_protection", STRING_TYPE, DeleteKeysResponse::getSlashingProtection)
          .build();

  public static final DeserializableTypeDefinition<DeleteKeysRequest> DELETE_KEYS_REQUEST =
      DeserializableTypeDefinition.object(DeleteKeysRequest.class)
          .name("DeleteKeysRequest")
          .initializer(DeleteKeysRequest::new)
          .withField(
              "pubkeys",
              DeserializableTypeDefinition.listOf(PUBKEY_API_TYPE),
              DeleteKeysRequest::getPublicKeys,
              DeleteKeysRequest::setPublicKeys)
          .build();

  public static final SerializableTypeDefinition<DeleteRemoteKeysResponse>
      DELETE_REMOTE_KEYS_RESPONSE_TYPE =
          SerializableTypeDefinition.object(DeleteRemoteKeysResponse.class)
              .name("DeleteRemoteKeysResponse")
              .withField("data", listOf(DELETE_KEY_RESULT), DeleteRemoteKeysResponse::getData)
              .build();

  public static final DeserializableTypeDefinition<String> BUILDER_URL_TYPE =
      DeserializableTypeDefinition.string(String.class)
          .formatter(Function.identity())
          .parser(Function.identity())
          .format("uri")
          .minLength(1)
          .maxLength((int) MAX_BUILDER_URL_SIZE)
          .description("The URL this entry's requests are sent to.")
          .example("https://builder.example.com")
          .build();

  public static final DeserializableTypeDefinition<Bytes> BUILDER_AUTH_DATA_TYPE =
      DeserializableTypeDefinition.string(Bytes.class)
          .formatter(Bytes::toHexString)
          .parser(Bytes::fromHexString)
          .pattern("^0x(?:[a-fA-F0-9]{2}){1," + MAX_BUILDER_AUTH_DATA_SIZE + "}$")
          .description(
              "Opaque data used to authenticate this validator's requests to the builder. "
                  + "Omitting it leaves the validator client to set it to the UTF-8 bytes of the entry's url.")
          .example("0x68747470733a2f2f6275696c6465722e6578616d706c652e636f6d")
          .build();

  public static final DeserializableTypeDefinition<BuilderEntry> BUILDER_ENTRY_TYPE =
      DeserializableTypeDefinition.object(BuilderEntry.class, BuilderEntry.Builder.class)
          .name("BuilderEntry")
          .initializer(BuilderEntry.Builder::new)
          .finisher(BuilderEntry.Builder::build)
          .withField("url", BUILDER_URL_TYPE, BuilderEntry::url, BuilderEntry.Builder::url)
          .withOptionalField(
              "auth_data",
              BUILDER_AUTH_DATA_TYPE,
              BuilderEntry::authData,
              BuilderEntry.Builder::authData)
          .withOptionalField(
              "builder_pubkeys",
              DeserializableTypeDefinition.listOf(PUBKEY_API_TYPE),
              BuilderEntry::builderPubkeys,
              BuilderEntry.Builder::builderPubkeys)
          .withOptionalField(
              "max_execution_payment",
              CoreTypes.UINT64_TYPE,
              BuilderEntry::maxExecutionPayment,
              BuilderEntry.Builder::maxExecutionPayment)
          .withOptionalField(
              "min_bid", CoreTypes.UINT64_TYPE, BuilderEntry::minBid, BuilderEntry.Builder::minBid)
          .withOptionalField(
              "builder_boost_factor",
              CoreTypes.UINT64_TYPE,
              BuilderEntry::builderBoostFactor,
              BuilderEntry.Builder::builderBoostFactor)
          .build();

  public static final DeserializableTypeDefinition<BuilderConfig> BUILDER_CONFIG_TYPE =
      DeserializableTypeDefinition.object(BuilderConfig.class, BuilderConfig.Builder.class)
          .name("BuilderConfig")
          .initializer(BuilderConfig.Builder::new)
          .finisher(BuilderConfig.Builder::build)
          .withOptionalField(
              "min_bid",
              CoreTypes.UINT64_TYPE,
              BuilderConfig::minBid,
              BuilderConfig.Builder::minBid)
          .withOptionalField(
              "builder_boost_factor",
              CoreTypes.UINT64_TYPE,
              BuilderConfig::builderBoostFactor,
              BuilderConfig.Builder::builderBoostFactor)
          .withOptionalField(
              "builders",
              DeserializableTypeDefinition.listOf(BUILDER_ENTRY_TYPE),
              BuilderConfig::builders,
              BuilderConfig.Builder::builders)
          .build();

  public static final ParameterMetadata<BLSPublicKey> PARAM_PUBKEY_TYPE =
      new ParameterMetadata<>(
          PUBKEY,
          new StringBasedPrimitiveTypeDefinition.StringTypeBuilder<BLSPublicKey>()
              .formatter(value -> value.toBytesCompressed().toHexString())
              .parser(value -> BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(value)))
              .pattern("^0x[a-fA-F0-9]{96}$")
              .example(
                  "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
              .build());

  public static final ParameterMetadata<UInt64> EPOCH_QUERY_TYPE =
      new ParameterMetadata<>(
          EPOCH, CoreTypes.UINT64_TYPE.withDescription(EPOCH_QUERY_DESCRIPTION));
}
