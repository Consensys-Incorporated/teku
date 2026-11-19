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

package tech.pegasys.teku.cli.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.model.Cipher;
import tech.pegasys.teku.bls.keystore.model.CipherFunction;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.teku.bls.keystore.model.Pbkdf2PseudoRandomFunction;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.ValidatorClientService;

class KnownValidatorKeysTest {
  private final SubCommandLogger log = mock(SubCommandLogger.class);

  @TempDir private Path tempDir;
  private DataDirLayout dataDirLayout;
  private int seed = 0;

  @BeforeEach
  void setUp() {
    dataDirLayout = DataDirLayout.createFrom(DataConfig.builder().dataBasePath(tempDir).build());
  }

  @Test
  void shouldReportNoKeySourcesWhenNothingIsConfigured() {
    final KnownValidatorKeys keys = load(List.of(), List.of());

    assertThat(keys.publicKeys()).isEmpty();
    assertThat(keys.uncheckedSources()).isEmpty();
    assertThat(keys.hasKeySources()).isFalse();

    keys.displayUncheckedSources(log);
    verify(log).display(startsWith("No validator keys were found"));
  }

  @Test
  void shouldReadKeyManagerLocalKeystoresWithoutPasswords() throws IOException {
    final Path localPath = ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    final BLSPublicKey key1 = writeKeystore(localPath.resolve("key1.json"));
    final BLSPublicKey key2 = writeKeystore(localPath.resolve("key2.json"));
    Files.writeString(localPath.resolve("notes.txt"), "not a keystore");

    final KnownValidatorKeys keys = load(List.of(), List.of());

    assertThat(keys.publicKeys()).containsExactly(key1, key2);
    assertThat(keys.uncheckedSources()).isEmpty();
    assertThat(keys.hasKeySources()).isTrue();
    verify(log).display("Found 2 validator keys from key-manager keystores in " + localPath);
  }

  @Test
  void shouldCountEmptyKeyManagerDirectoryAsKeySource() throws IOException {
    Files.createDirectories(ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout));

    final KnownValidatorKeys keys = load(List.of(), List.of());

    assertThat(keys.publicKeys()).isEmpty();
    assertThat(keys.hasKeySources()).isTrue();
    keys.displayUncheckedSources(log);
    verify(log, never()).display(startsWith("No validator keys were found"));
  }

  @Test
  void shouldReportUnreadableKeyManagerKeystore() throws IOException {
    final Path localPath = ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    final BLSPublicKey key = writeKeystore(localPath.resolve("key1.json"));
    final Path corrupt = localPath.resolve("corrupt.json");
    Files.writeString(corrupt, "{\"not\": \"a keystore\"}", StandardCharsets.UTF_8);

    final KnownValidatorKeys keys = load(List.of(), List.of());

    assertThat(keys.publicKeys()).containsExactly(key);
    assertThat(keys.uncheckedSources()).singleElement().asString().startsWith(corrupt.toString());

    keys.displayUncheckedSources(log);
    verify(log).display("These keys were not checked for a missing slashing protection record:");
    verify(log).display(startsWith(" - " + corrupt));
  }

  @Test
  void shouldReadKeyManagerRemoteKeys() throws IOException {
    final Path remotePath = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);
    Files.createDirectories(remotePath);
    final BLSPublicKey withUrl = BLSTestUtil.randomPublicKey(seed++);
    final BLSPublicKey withoutUrl = BLSTestUtil.randomPublicKey(seed++);
    // the format written by ExternalValidatorSource when a key is imported
    Files.writeString(
        remotePath.resolve("a.json"),
        "{\"pubkey\":\"" + withUrl + "\",\"url\":\"http://localhost:9000\"}");
    Files.writeString(remotePath.resolve("b.json"), "{\"pubkey\":\"" + withoutUrl + "\"}");
    Files.writeString(remotePath.resolve("c.json"), "{\"url\":\"http://localhost:9000\"}");

    final KnownValidatorKeys keys = load(List.of(), List.of());

    assertThat(keys.publicKeys()).containsExactly(withUrl, withoutUrl);
    assertThat(keys.uncheckedSources())
        .singleElement()
        .asString()
        .startsWith(remotePath.resolve("c.json").toString());
    verify(log).display("Found 2 validator keys from key-manager remote keys in " + remotePath);
  }

  @Test
  void shouldReadValidatorKeysOptionWithoutDecrypting() throws IOException {
    final Path keysDir = tempDir.resolve("keys");
    final Path passwordsDir = tempDir.resolve("passwords");
    final BLSPublicKey dirKey = writeKeystore(keysDir.resolve("dir-key.json"));
    writePassword(passwordsDir.resolve("dir-key.txt"));
    final Path singleKeystore = tempDir.resolve("single.json");
    final Path singlePassword = tempDir.resolve("single.txt");
    final BLSPublicKey singleKey = writeKeystore(singleKeystore);
    // the password is wrong, but the keystore is never decrypted
    Files.writeString(singlePassword, "wrong password");

    final KnownValidatorKeys keys =
        load(
            List.of(
                keysDir + File.pathSeparator + passwordsDir,
                singleKeystore + File.pathSeparator + singlePassword),
            List.of());

    assertThat(keys.publicKeys()).containsExactlyInAnyOrder(dirKey, singleKey);
    assertThat(keys.uncheckedSources()).isEmpty();
    verify(log).display("Found 2 validator keys from keystores given by --validator-keys");
  }

  @Test
  void shouldReportInvalidValidatorKeysOption() {
    final Path missing = tempDir.resolve("missing.json");

    final KnownValidatorKeys keys =
        load(List.of(missing + File.pathSeparator + tempDir.resolve("missing.txt")), List.of());

    assertThat(keys.publicKeys()).isEmpty();
    assertThat(keys.hasKeySources()).isTrue();
    assertThat(keys.uncheckedSources())
        .singleElement()
        .asString()
        .startsWith("--validator-keys: ")
        .contains(missing.toString());
  }

  @Test
  void shouldReadLiteralExternalSignerPublicKeys() {
    final BLSPublicKey key1 = BLSTestUtil.randomPublicKey(seed++);
    final BLSPublicKey key2 = BLSTestUtil.randomPublicKey(seed++);

    final KnownValidatorKeys keys =
        load(List.of(), List.of(key1.toString(), key2.toBytesCompressed().toUnprefixedHexString()));

    assertThat(keys.publicKeys()).containsExactly(key1, key2);
    assertThat(keys.uncheckedSources()).isEmpty();
    verify(log)
        .display(
            "Found 2 validator keys from keys given by --validators-external-signer-public-keys");
  }

  @Test
  void shouldNotFetchExternalSignerPublicKeysFromUrls() {
    final BLSPublicKey literal = BLSTestUtil.randomPublicKey(seed++);

    final KnownValidatorKeys keys =
        load(
            List.of(),
            List.of("http://localhost:1/keys", "external-signer", literal.toString(), "0x1234"));

    assertThat(keys.publicKeys()).containsExactly(literal);
    assertThat(keys.uncheckedSources())
        .containsExactly(
            "--validators-external-signer-public-keys: keys at http://localhost:1/keys were not"
                + " fetched",
            "--validators-external-signer-public-keys: keys held by the external signer were not"
                + " fetched",
            "--validators-external-signer-public-keys: 0x1234 is not a valid public key");
  }

  @Test
  void shouldCombineSourcesInOrderWithoutDuplicates() throws IOException {
    final Path localPath = ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    final Path localKeystore = localPath.resolve("local.json");
    final BLSPublicKey localKey = writeKeystore(localKeystore);
    final Path remotePath = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);
    Files.createDirectories(remotePath);
    final BLSPublicKey remoteKey = BLSTestUtil.randomPublicKey(seed++);
    Files.writeString(remotePath.resolve("remote.json"), "{\"pubkey\":\"" + remoteKey + "\"}");
    final Path password = tempDir.resolve("password.txt");
    writePassword(password);
    final BLSPublicKey externalKey = BLSTestUtil.randomPublicKey(seed++);

    final KnownValidatorKeys keys =
        load(
            // the key-manager keystore is also given by --validator-keys
            List.of(localKeystore + File.pathSeparator + password),
            List.of(externalKey.toString(), remoteKey.toString()));

    assertThat(keys.publicKeys()).containsExactly(localKey, remoteKey, externalKey);
    keys.displayUncheckedSources(log);
    verify(log, never()).display(startsWith("These keys were not checked"));
    verify(log, never()).display(startsWith(" - "));
  }

  private KnownValidatorKeys load(
      final List<String> validatorKeys, final List<String> externalSignerPublicKeys) {
    final KnownValidatorKeys keys =
        KnownValidatorKeys.load(log, dataDirLayout, validatorKeys, externalSignerPublicKeys);
    verify(log, never()).error(anyString());
    return keys;
  }

  private BLSPublicKey writeKeystore(final Path keystorePath) throws IOException {
    return writeKeystore(keystorePath, BLSTestUtil.randomKeyPair(seed++));
  }

  static BLSPublicKey writeKeystore(final Path keystorePath, final BLSKeyPair keyPair)
      throws IOException {
    Files.createDirectories(keystorePath.getParent());
    KeyStoreLoader.saveToFile(
        keystorePath,
        KeyStore.encrypt(
            keyPair.getSecretKey().toBytes(),
            keyPair.getPublicKey().toBytesCompressed(),
            "password",
            "",
            new Pbkdf2Param(32, 1, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.random()),
            new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16))));
    return keyPair.getPublicKey();
  }

  private void writePassword(final Path passwordPath) throws IOException {
    Files.createDirectories(passwordPath.getParent());
    Files.writeString(passwordPath, "password");
  }
}
