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

package tech.pegasys.teku.bls.keystore.pq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.model.SCryptParam;

class PqKeyStoreTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PASSWORD = "testpassword";
  private static final PqKeyStoreSecrets SECRETS =
      new PqKeyStoreSecrets(
          Bytes32.fromHexString(
              "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"),
          Bytes32.fromHexString(
              "0xa1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0"));
  private static final Bytes32 XMSS_PUBKEY = Bytes32.fromHexStringLenient("0x1234");
  private static final Bytes32 VRF_ROOT = Bytes32.fromHexStringLenient("0x5678");
  private static final Bytes BLS_PUBKEY = Bytes.repeat((byte) 0xab, 48);
  private static final Cipher CIPHER =
      new Cipher(Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6"));
  private static final Pbkdf2Param KDF =
      new Pbkdf2Param(32, 2, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.ZERO);

  @TempDir Path tempDir;

  @Test
  void encrypt_roundTripsBothSeeds() {
    final PqKeyStoreData data = encrypt(Optional.of(BLS_PUBKEY));

    assertThat(data.getFormat()).isEqualTo(PqKeyStoreData.FORMAT);
    assertThat(data.getVersion()).isEqualTo(PqKeyStoreData.VERSION);
    assertThat(data.getXmssPubkey()).isEqualTo(XMSS_PUBKEY);
    assertThat(data.getVrfRoot()).isEqualTo(VRF_ROOT);
    assertThat(data.getBlsPubkey()).contains(BLS_PUBKEY);
    assertThat(data.getUuid()).isNotNull();
    assertThat(PqKeyStore.validatePassword(PASSWORD, data)).isTrue();
    assertThat(PqKeyStore.decrypt(PASSWORD, data)).isEqualTo(SECRETS);
  }

  @Test
  void encrypt_encryptsXmssSeedThenVrfSeed() {
    final PqKeyStoreData data = encrypt(Optional.empty());

    // AES-128-CTR is a stream cipher, so the ciphertext is the secret's length
    assertThat(data.getCrypto().getCipher().getMessage().size())
        .isEqualTo(PqKeyStoreData.SECRET_SIZE);
    assertThat(KeyStore.decrypt(PASSWORD, data.getCrypto()))
        .isEqualTo(Bytes.concatenate(SECRETS.xmssSeed(), SECRETS.vrfSeed()));
  }

  @Test
  void decrypt_rejectsWrongPassword() {
    final PqKeyStoreData data = encrypt(Optional.empty());

    assertThat(PqKeyStore.validatePassword("wrong", data)).isFalse();
    assertThatThrownBy(() -> PqKeyStore.decrypt("wrong", data))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("checksum");
  }

  @Test
  void saveAndLoad_roundTrips() throws IOException {
    final PqKeyStoreData data = encrypt(Optional.of(BLS_PUBKEY));
    final Path file = tempDir.resolve("key.pq-keystore");

    PqKeyStore.saveToNewFile(file, data);
    final PqKeyStoreData loaded = PqKeyStore.loadFromFile(file);

    assertThat(loaded.getXmssPubkey()).isEqualTo(XMSS_PUBKEY);
    assertThat(loaded.getVrfRoot()).isEqualTo(VRF_ROOT);
    assertThat(loaded.getBlsPubkey()).contains(BLS_PUBKEY);
    assertThat(loaded.getUuid()).isEqualTo(data.getUuid());
    assertThat(PqKeyStore.decrypt(PASSWORD, loaded)).isEqualTo(SECRETS);
  }

  @Test
  void saveAndLoad_roundTripsScryptKeystore() throws IOException {
    final PqKeyStoreData data =
        PqKeyStore.encrypt(
            SECRETS,
            XMSS_PUBKEY,
            VRF_ROOT,
            Optional.empty(),
            PASSWORD,
            new SCryptParam(32, 2, 1, 8, Bytes32.ZERO),
            CIPHER);
    final Path file = tempDir.resolve("key.pq-keystore");

    PqKeyStore.saveToNewFile(file, data);

    assertThat(PqKeyStore.decrypt(PASSWORD, PqKeyStore.loadFromFile(file))).isEqualTo(SECRETS);
  }

  @Test
  void toJson_writesTheDocumentedFields() throws IOException {
    final JsonNode json =
        OBJECT_MAPPER.readTree(PqKeyStore.toJson(encrypt(Optional.of(BLS_PUBKEY))));

    assertThat(json.get("format").asText()).isEqualTo("teku-pq-validator-keystore");
    assertThat(json.get("version").asInt()).isEqualTo(1);
    assertThat(json.get("xmss_pubkey").asText()).isEqualTo(XMSS_PUBKEY.toUnprefixedHexString());
    assertThat(json.get("vrf_root").asText()).isEqualTo(VRF_ROOT.toUnprefixedHexString());
    assertThat(json.get("bls_pubkey").asText()).isEqualTo(BLS_PUBKEY.toUnprefixedHexString());
    assertThat(json.get("crypto").get("kdf").get("function").asText()).isEqualTo("pbkdf2");
    assertThat(json.get("uuid")).isNotNull();
  }

  @Test
  void toJson_omitsAbsentBlsPubkey() throws IOException {
    final JsonNode json = OBJECT_MAPPER.readTree(PqKeyStore.toJson(encrypt(Optional.empty())));

    assertThat(json.has("bls_pubkey")).isFalse();
    assertThat(PqKeyStore.loadFromString(json.toString()).getBlsPubkey()).isEmpty();
  }

  @Test
  void saveToNewFile_refusesToReplaceAnExistingFile() throws IOException {
    final Path file = tempDir.resolve("key.pq-keystore");
    Files.writeString(file, "existing");

    assertThatThrownBy(() -> PqKeyStore.saveToNewFile(file, encrypt(Optional.empty())))
        .isInstanceOf(FileAlreadyExistsException.class);
    assertThat(Files.readString(file)).isEqualTo("existing");
  }

  @Test
  void load_rejectsUnknownFormat() throws IOException {
    assertThatThrownBy(() -> PqKeyStore.loadFromString(withField("format", "eip-2335")))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("Not a PQ validator keystore");
  }

  @Test
  void load_rejectsUnsupportedVersion() throws IOException {
    final ObjectNode json = jsonOf(encrypt(Optional.empty()));
    json.put("version", 2);

    assertThatThrownBy(() -> PqKeyStore.loadFromString(json.toString()))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("version 2 is not supported");
  }

  @Test
  void load_rejectsWrongSizedBlsPubkey() throws IOException {
    assertThatThrownBy(() -> PqKeyStore.loadFromString(withField("bls_pubkey", "abcd")))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("bls_pubkey");
  }

  @Test
  void load_rejectsWrongSizedXmssPubkey() throws IOException {
    assertThatThrownBy(() -> PqKeyStore.loadFromString(withField("xmss_pubkey", "abcd")))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("xmss_pubkey");
    assertThatThrownBy(() -> PqKeyStore.loadFromString(withField("vrf_root", "abcd")))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("vrf_root");
  }

  @Test
  void load_rejectsMissingRequiredField() throws IOException {
    final ObjectNode json = jsonOf(encrypt(Optional.empty()));
    json.remove("vrf_root");

    assertThatThrownBy(() -> PqKeyStore.loadFromString(json.toString()))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("vrf_root");
  }

  @Test
  void load_rejectsBlsKeystore() {
    final String blsKeystore =
        """
        {"crypto": {}, "pubkey": "abcd", "version": 4}
        """;
    assertThatThrownBy(() -> PqKeyStore.loadFromString(blsKeystore))
        .isInstanceOf(KeyStoreValidationException.class);
  }

  @Test
  void load_reportsMissingFile() {
    assertThatThrownBy(() -> PqKeyStore.loadFromFile(tempDir.resolve("missing.pq-keystore")))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void decrypt_rejectsSecretOfWrongLength() {
    final PqKeyStoreData data =
        new PqKeyStoreData(
            XMSS_PUBKEY,
            VRF_ROOT,
            Optional.empty(),
            KeyStore.encryptSecret(SECRETS.xmssSeed(), PASSWORD, KDF, CIPHER));

    assertThatThrownBy(() -> PqKeyStore.decrypt(PASSWORD, data))
        .isInstanceOf(KeyStoreValidationException.class)
        .hasMessageContaining("Expected 64 bytes");
  }

  @Test
  void secrets_toStringRedactsSeeds() {
    assertThat(SECRETS.toString())
        .doesNotContain(SECRETS.xmssSeed().toUnprefixedHexString())
        .doesNotContain(SECRETS.vrfSeed().toUnprefixedHexString());
  }

  private static PqKeyStoreData encrypt(final Optional<Bytes> blsPubkey) {
    return PqKeyStore.encrypt(SECRETS, XMSS_PUBKEY, VRF_ROOT, blsPubkey, PASSWORD, KDF, CIPHER);
  }

  private static ObjectNode jsonOf(final PqKeyStoreData data) throws IOException {
    return (ObjectNode) OBJECT_MAPPER.readTree(PqKeyStore.toJson(data));
  }

  private static String withField(final String field, final String value) throws IOException {
    final ObjectNode json = jsonOf(encrypt(Optional.of(BLS_PUBKEY)));
    json.put(field, value);
    return json.toString();
  }
}
