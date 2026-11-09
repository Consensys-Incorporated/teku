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

package tech.pegasys.teku.bls.keystore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.KdfParam;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.bls.keystore.model.SCryptParam;

class KeyStoreTest {
  private static final int DKLEN = 32;
  private static final int ITERATIVE_COUNT = 262144;
  private static final int MEMORY_CPU_COST = 262144;
  private static final int PARALLELIZATION = 1;
  private static final int BLOCKSIZE = 8;

  private static final String PASSWORD =
      "\uD835\uDD31\uD835\uDD22\uD835\uDD30\uD835\uDD31\uD835\uDD2D\uD835\uDD1E\uD835\uDD30\uD835\uDD30\uD835\uDD34\uD835\uDD2C\uD835\uDD2F\uD835\uDD21\uD83D\uDD11";
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes.fromHexString("0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final Bytes BLS_PUB_KEY =
      Bytes.fromHexString(
          "9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");

  private static final String SCRYPT_KEYSTORE_RESOURCE = "scryptTestVector.json";
  private static final String SCRYPT_EXTRA_FIELD_KEYSTORE_RESOURCE =
      "scryptExtraFieldTestVector.json";
  private static final String SCRYPT_MISSING_UUID_PATH_KEYSTORE_RESOURCE =
      "scryptTestVectorWithMissingPathAndUUID.json";
  private static final String PBKDF2_KEYSTORE_RESOURCE = "pbkdf2TestVector.json";
  private static final String MISSING_SECTION_KEYSTORE_RESOURCE =
      "missingKdfSectionTestVector.json";
  private static final String UNSUPPORTED_VERSION_JSON_RESOURCE = "v3TestVector.json";
  private static final String UNSUPPORTED_CHECKSUM_FUNCTION_JSON =
      "unsupportedChecksumFunction.json";
  private static final String UNSUPPORTED_CIPHER_FUNCTION_JSON = "unsupportedCipherFunction.json";
  private static final String UNSUPPORTED_KDF_FUNCTION_JSON = "unsupportedKdfFunction.json";
  private static final String UNSUPPORTED_PBKDF2_PRF_FUNCTION_JSON = "unsupportedPBKDF2Prf.json";
  private static final String UNSUPPORTED_DKLEN_FUNCTION_JSON = "unsupportedDkLen.json";
  private static final Cipher CIPHER = new Cipher(AES_IV_PARAM);

  public static Stream<Arguments> encryptWithKdfAndCipherArguments() {
    // KdfParam, expected checksum, expected encrypted cipher message
    return Stream.of(
        Arguments.of(
            new SCryptParam(DKLEN, MEMORY_CPU_COST, PARALLELIZATION, BLOCKSIZE, SALT),
            Bytes.fromHexString("d2217fe5f3e9a1e34581ef8a78f7c9928e436d36dacc5e846690a5581e8ea484"),
            Bytes.fromHexString(
                "06ae90d55fe0a6e9c5c3bc5b170827b2e5cce3929ed3f116c2811e6366dfe20f")),
        Arguments.of(
            new Pbkdf2Param(DKLEN, ITERATIVE_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, SALT),
            Bytes.fromHexString("8a9f5d9912ed7e75ea794bc5a89bca5f193721d30868ade6f73043c6ea6febf1"),
            Bytes.fromHexString(
                "cee03fde2af33149775b7223e7845e4fb2c8ae1792e5f99fe9ecf474cc8c16ad")));
  }

  public static Stream<Arguments> resourceLoaderErrorConditions() {
    return Stream.of(
        Arguments.of(
            "Invalid json",
            MISSING_SECTION_KEYSTORE_RESOURCE,
            "Invalid KeyStore: Missing property 'params' for external type id 'function'"),
        Arguments.of(
            "Unsupported version",
            UNSUPPORTED_VERSION_JSON_RESOURCE,
            "The KeyStore version 3 is not supported"),
        Arguments.of(
            "Unsupported checksum fn",
            UNSUPPORTED_CHECKSUM_FUNCTION_JSON,
            "Checksum function [sha128] is not supported."),
        Arguments.of(
            "Unsupported cipher fn",
            UNSUPPORTED_CIPHER_FUNCTION_JSON,
            "Cipher function [aes-256-ctr] is not supported."),
        Arguments.of(
            "Unsupported kdf fn",
            UNSUPPORTED_KDF_FUNCTION_JSON,
            "Kdf function [pbkdf3] is not supported."),
        Arguments.of(
            "Unsupported pbkdf2 fn",
            UNSUPPORTED_PBKDF2_PRF_FUNCTION_JSON,
            "PBKDF2 pseudorandom function (prf) [hmac-sha512] is not supported."),
        Arguments.of(
            "Unsupported dklen fn",
            UNSUPPORTED_DKLEN_FUNCTION_JSON,
            "Generated key length parameter dklen must be >= 32."));
  }

  @Test
  void loadSCryptKeyStoreAndDecryptKey() {
    loadKeyStoreAndDecryptKey(SCRYPT_KEYSTORE_RESOURCE);
  }

  @Test
  void ableToLoadKeystoreWithUnknownFieldsAndDecryptKey() {
    loadKeyStoreAndDecryptKey(SCRYPT_EXTRA_FIELD_KEYSTORE_RESOURCE);
  }

  @Test
  void ableToLoadKeystoreWithMissingUUIDAndPathFieldsAndDecryptKey() {
    loadKeyStoreAndDecryptKey(SCRYPT_MISSING_UUID_PATH_KEYSTORE_RESOURCE);
  }

  @Test
  void loadPBKDF2KeyStoreAndDecryptKey() {
    loadKeyStoreAndDecryptKey(PBKDF2_KEYSTORE_RESOURCE);
  }

  @Test
  void loadSCryptKeyStoreAndTestInvalidPassword() {
    invalidPasswordValidation(SCRYPT_KEYSTORE_RESOURCE);
  }

  @Test
  void shouldLoadKeystoreDataFromStringInput() throws IOException {
    loadKeystoreFromString(SCRYPT_KEYSTORE_RESOURCE);
  }

  @Test
  void loadPBKDF2KeyStoreAndTestInvalidPassword() {
    invalidPasswordValidation(PBKDF2_KEYSTORE_RESOURCE);
  }

  private void loadKeyStoreAndDecryptKey(final String resourcePath) {
    final KeyStoreData keyStoreData = loadKeyStoreFromResource(resourcePath);
    final Bytes decryptedBlsPrivateKey = KeyStore.decrypt(PASSWORD, keyStoreData);
    assertThat(decryptedBlsPrivateKey).isEqualTo(BLS_PRIVATE_KEY);
  }

  private void invalidPasswordValidation(final String resourcePath) {
    final KeyStoreData keyStoreData = loadKeyStoreFromResource(resourcePath);
    assertThat(KeyStore.validatePassword("invalidpassword", keyStoreData)).isFalse();
  }

  private KeyStoreData loadKeyStoreFromResource(final String resourcePath) {
    final URL resource = Resources.getResource(KeyStoreTest.class, resourcePath);
    return KeyStoreLoader.loadFromUrl(resource);
  }

  @ParameterizedTest
  @MethodSource("encryptWithKdfAndCipherArguments")
  void encryptWithKdfAndCipherFunction(
      final KdfParam kdfParam, final Bytes expectedChecksum, final Bytes encryptedCipherMessage) {
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(BLS_PRIVATE_KEY, BLS_PUB_KEY, PASSWORD, "", kdfParam, CIPHER);
    assertThat(keyStoreData.getCrypto().getChecksum().getMessage()).isEqualTo(expectedChecksum);
    assertThat(keyStoreData.getCrypto().getCipher().getMessage()).isEqualTo(encryptedCipherMessage);
    assertThat(keyStoreData.getVersion()).isEqualTo(KeyStoreData.KEYSTORE_VERSION);
    assertThat(keyStoreData.getPubkey()).isEqualTo(BLS_PUB_KEY);
    assertThat(keyStoreData.getUuid()).isNotNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourceLoaderErrorConditions")
  void shouldErrorLoadingFromFile(
      final String description, final String resource, final String messageStartsWith) {
    assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeyStoreFromResource(resource))
        .withMessageStartingWith(messageStartsWith);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourceLoaderErrorConditions")
  void shouldErrorLoadingFromString(
      final String description, final String resource, final String messageStartsWith) {
    assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> loadKeystoreFromString(resource))
        .withMessageStartingWith(messageStartsWith);
  }

  @Test
  void encryptUsingSCryptAndSaveKeyStore(@TempDir final Path tempDir) throws IOException {
    final KdfParam kdfParam =
        new SCryptParam(DKLEN, MEMORY_CPU_COST, PARALLELIZATION, BLOCKSIZE, SALT);
    encryptSaveAndReloadKeyStore(tempDir, kdfParam);
  }

  @Test
  void loadingNonExistentKeyStoreFileThrowsError(@TempDir final Path tempDir) {
    final Path keyStoreFile = tempDir.resolve("nonexistent.json");
    assertThatExceptionOfType(KeyStoreValidationException.class)
        .isThrownBy(() -> KeyStoreLoader.loadFromFile(keyStoreFile.toUri()))
        .withMessageStartingWith("KeyStore file not found: file:")
        .withMessageEndingWith(keyStoreFile.getFileName().toString());
  }

  @Test
  void encryptUsingPBKDF2AndSaveKeyStore(@TempDir final Path tempDir) throws IOException {
    final KdfParam kdfParam =
        new Pbkdf2Param(DKLEN, ITERATIVE_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, SALT);
    encryptSaveAndReloadKeyStore(tempDir, kdfParam);
  }

  private void encryptSaveAndReloadKeyStore(final Path tempDir, final KdfParam kdfParam)
      throws IOException {
    final KeyStoreData keyStoreData =
        KeyStore.encrypt(BLS_PRIVATE_KEY, BLS_PUB_KEY, PASSWORD, "", kdfParam, CIPHER);
    final Path tempKeyStoreFile = Files.createTempFile(tempDir, "keystore", ".json");
    assertThatCode(() -> KeyStoreLoader.saveToFile(tempKeyStoreFile, keyStoreData))
        .doesNotThrowAnyException();

    // reload it back
    final KeyStoreData loadedKeyStore = KeyStoreLoader.loadFromFile(tempKeyStoreFile.toUri());
    assertThat(loadedKeyStore.getUuid()).isEqualByComparingTo(keyStoreData.getUuid());
    assertThat(loadedKeyStore.getCrypto().getChecksum().getMessage())
        .isEqualTo(keyStoreData.getCrypto().getChecksum().getMessage());
  }

  KeyStoreData loadKeystoreFromString(final String resourceFileName) throws IOException {
    final String keystoreString =
        Resources.toString(
            Resources.getResource(KeyStoreTest.class, resourceFileName), StandardCharsets.UTF_8);
    return KeyStoreLoader.loadFromString(keystoreString);
  }
}
