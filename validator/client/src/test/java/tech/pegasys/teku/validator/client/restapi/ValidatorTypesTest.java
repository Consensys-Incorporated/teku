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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.parse;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostRemoteKeysRequest;

class ValidatorTypesTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void listKeysResponse_shouldFormatListOfPublicKeys() throws Exception {
    final List<Validator> keys =
        List.of(
            new Validator(dataStructureUtil.randomPublicKey(), NO_OP_SIGNER, Optional::empty, true),
            new Validator(
                dataStructureUtil.randomPublicKey(), NO_OP_SIGNER, Optional::empty, false));

    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(keys, ValidatorTypes.LIST_KEYS_RESPONSE_TYPE));

    assertThat(result).containsOnlyKeys("data");
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    assertThat(keysList).hasSize(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      assertThat(keysList.get(i))
          .containsOnly(
              entry(
                  "validating_pubkey",
                  keys.get(i).getPublicKey().toBytesCompressed().toHexString()),
              entry("readonly", keys.get(i).isReadOnly()));
    }
  }

  @Test
  void postKeysRequest_shouldFormatPostKeysRequest() throws Exception {
    final PostKeysRequest request =
        new PostKeysRequest(List.of("key"), List.of("pass"), Optional.of("slashingprotection"));
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(request, ValidatorTypes.POST_KEYS_REQUEST));
    assertThat(result)
        .containsOnly(
            entry("keystores", List.of("key")),
            entry("passwords", List.of("pass")),
            entry("slashing_protection", "slashingprotection"));
  }

  @Test
  void postKeysRequest_shouldFormatPostKeysRequestWithoutSlashingProtection() throws Exception {
    final PostKeysRequest request =
        new PostKeysRequest(List.of("key"), List.of("pass"), Optional.empty());
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(request, ValidatorTypes.POST_KEYS_REQUEST));
    assertThat(result)
        .containsOnly(entry("keystores", List.of("key")), entry("passwords", List.of("pass")));
  }

  @Test
  void postKeysResponse_shouldFormatPostKeysResponse() throws Exception {
    final List<PostKeyResult> original =
        List.of(PostKeyResult.success(), PostKeyResult.error("message"), PostKeyResult.duplicate());
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(original, ValidatorTypes.POST_KEYS_RESPONSE));
    assertThat(result.keySet()).containsOnly("data");
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    for (int i = 0; i < original.size(); i++) {
      if (original.get(i).getMessage().isPresent()) {
        assertThat(keysList.get(i))
            .containsOnly(
                entry("status", original.get(i).getImportStatus().toString()),
                entry("message", original.get(i).getMessage().orElseThrow()));
      } else {
        assertThat(keysList.get(i))
            .containsOnly(entry("status", original.get(i).getImportStatus().toString()));
      }
    }
  }

  @Test
  void deleteKeysRequest_shouldDeserializeData() throws Exception {
    final List<BLSPublicKey> keys =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    DeleteKeysRequest request = new DeleteKeysRequest();
    request.setPublicKeys(keys);
    assertRoundTrip(request, ValidatorTypes.DELETE_KEYS_REQUEST);
  }

  @Test
  void deleteKeysResponse_shouldSerialize() throws Exception {
    final String slashString = "\"{}\"";
    final List<DeleteKeyResult> original =
        List.of(
            DeleteKeyResult.success(),
            DeleteKeyResult.error("ERR"),
            DeleteKeyResult.notActive(),
            DeleteKeyResult.notFound());
    final Map<String, Object> result =
        JsonTestUtil.parse(
            JsonUtil.serialize(
                new DeleteKeysResponse(original, slashString),
                ValidatorTypes.DELETE_KEYS_RESPONSE_TYPE));
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    for (int i = 0; i < original.size(); i++) {
      if (original.get(i).getMessage().isPresent()) {
        assertThat(keysList.get(i))
            .containsOnly(
                entry("status", original.get(i).getStatus().toString()),
                entry("message", original.get(i).getMessage().orElseThrow()));
      } else {
        assertThat(keysList.get(i))
            .containsOnly(entry("status", original.get(i).getStatus().toString()));
      }
    }
    assertThat(result.get("slashing_protection")).isEqualTo(slashString);
  }

  @Test
  void deleteKeysRequest_shouldDeserializeEmptyList() throws Exception {
    assertRoundTrip(new DeleteKeysRequest(), ValidatorTypes.DELETE_KEYS_REQUEST);
  }

  private <T> void assertRoundTrip(final T value, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    final T result = parse(serialize(value, type), type);
    assertThat(result).isEqualTo(value);
  }

  @SuppressWarnings("unchecked")
  @Test
  void postRemoteKeysRequest_shouldFormatPostRemoteKeysRequestOptionalUrl() throws Exception {
    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();

    final List<ExternalValidator> externalValidators =
        List.of(
            new ExternalValidator(publicKey1, Optional.empty()),
            new ExternalValidator(publicKey2, Optional.of(URI.create("http://host.com").toURL())));
    final PostRemoteKeysRequest request = new PostRemoteKeysRequest(externalValidators);
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(request, ValidatorTypes.POST_REMOTE_KEYS_REQUEST));

    assertThat(result).containsOnlyKeys("remote_keys").isInstanceOf(HashMap.class);

    final List<Map<String, Object>> remoteKeys =
        (List<Map<String, Object>>) result.get("remote_keys");
    assertThat(remoteKeys)
        .containsExactly(
            Map.of("pubkey", publicKey1.toString()),
            Map.of(
                "pubkey", publicKey2.toString(),
                "url", URI.create("http://host.com").toURL().toString()));
  }

  @Test
  void externalValidatorType_missingReadOnlyField() {
    assertThatThrownBy(
            () ->
                parse(
                    "{ \"pubkey\": \"0xa4654ac3105a58c7634031b5718c4880c87300f72091cfbc69fe490b71d93a671e00e80a388e1ceb8ea1de112003e976\"}",
                    ValidatorTypes.EXTERNAL_VALIDATOR_RESPONSE_TYPE))
        .isInstanceOf(MissingRequiredFieldException.class);
  }

  @Test
  void externalValidatorType_invalidUrl() {
    assertThatThrownBy(
            () ->
                parse(
                    "{ \"pubkey\": \"0xa4654ac3105a58c7634031b5718c4880c87300f72091cfbc69fe490b71d93a671e00e80a388e1ceb8ea1de112003e976\", \"url\": \"/\", \"readonly\": true}",
                    ValidatorTypes.EXTERNAL_VALIDATOR_RESPONSE_TYPE))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builderConfigType_roundTrip() throws Exception {
    final BuilderEntry fullBuilderEntry =
        new BuilderEntry(
            "https://builder-a.example.com",
            Optional.of(dataStructureUtil.randomBytes(32)),
            Optional.of(
                List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey())),
            Optional.of(dataStructureUtil.randomUInt64()),
            Optional.of(dataStructureUtil.randomUInt64()),
            Optional.of(dataStructureUtil.randomUInt64()));
    final BuilderEntry minimalBuilderEntry =
        new BuilderEntry(
            "https://builder-b.example.com",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final BuilderConfig builderConfig =
        new BuilderConfig(
            Optional.of(dataStructureUtil.randomUInt64()),
            Optional.of(dataStructureUtil.randomUInt64()),
            Optional.of(List.of(fullBuilderEntry, minimalBuilderEntry)));
    assertRoundTrip(builderConfig, ValidatorTypes.BUILDER_CONFIG_TYPE);
  }

  @Test
  void externalValidatorType_roundTrip() throws Exception {
    final ExternalValidator externalValidator =
        new ExternalValidator(dataStructureUtil.randomPublicKey(), Optional.empty(), true);
    assertRoundTrip(externalValidator, ValidatorTypes.EXTERNAL_VALIDATOR_RESPONSE_TYPE);
  }

  @Test
  void externalValidatorStore_urlPresent() throws JsonProcessingException, MalformedURLException {
    BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    checkExternalValidatorStoreRoundTrip(
        publicKey,
        Optional.of(URI.create("http://host.com").toURL()),
        "{\"pubkey\":\"" + publicKey + "\",\"url\":\"http://host.com\"}");
  }

  @Test
  void externalValidatorStore_noUrlProvided() throws JsonProcessingException {
    BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    checkExternalValidatorStoreRoundTrip(
        publicKey, Optional.empty(), "{\"pubkey\":\"" + publicKey + "\"}");
  }

  private void checkExternalValidatorStoreRoundTrip(
      final BLSPublicKey publicKey, final Optional<URL> url, final String expected)
      throws JsonProcessingException {
    ExternalValidator value = new ExternalValidator(publicKey, url);

    String serializedValue = serialize(value, ValidatorTypes.EXTERNAL_VALIDATOR_STORE);
    assertThat(serializedValue).isEqualTo(expected);

    ExternalValidator deserializedResult =
        parse(serializedValue, ValidatorTypes.EXTERNAL_VALIDATOR_STORE);
    assertThat(deserializedResult).isEqualTo(value);
  }
}
