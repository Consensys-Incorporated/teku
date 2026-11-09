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

package tech.pegasys.teku.validator.client.loader;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;

class LocalValidatorSourceTest {

  private static final String EXPECTED_PASSWORD = "testpassword";
  private static final Bytes32 BLS_PRIVATE_KEY =
      Bytes32.fromHexString("0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final BLSPublicKey PBKDF2_PUBKEY =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07"));
  private static final BLSKeyPair EXPECTED_BLS_KEY_PAIR =
      new BLSKeyPair(BLSSecretKey.fromBytes(BLS_PRIVATE_KEY));

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final KeystoreLocker keystoreLocker = mock(KeystoreLocker.class);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final KeyStoreFilesLocator keyStoreFilesLocator = mock(KeyStoreFilesLocator.class);

  private final LocalValidatorSource validatorSource =
      new LocalValidatorSource(
          spec, true, keystoreLocker, keyStoreFilesLocator, asyncRunner, true, Optional.empty());

  @Test
  void shouldLoadKeysFromKeyStores(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());
    final Path pbkdf2Keystore = Path.of(Resources.getResource("pbkdf2TestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(
            Pair.of(scryptKeystore, tempPasswordFile), Pair.of(pbkdf2Keystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    final List<ValidatorProvider> availableValidators = validatorSource.getAvailableValidators();
    assertThat(availableValidators).hasSize(2);
    // Both keystores encrypt the same key.
    assertProviderMatchesKey(availableValidators.get(0), EXPECTED_BLS_KEY_PAIR);
    assertProviderMatchesKey(availableValidators.get(1), EXPECTED_BLS_KEY_PAIR);
  }

  @Test
  void shouldThrowExceptionWhenPasswordFileIsEmpty(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Keystore password cannot be empty: " + tempPasswordFile);
  }

  @Test
  void shouldThrowExceptionWhenPasswordIsIncorrect(@TempDir final Path tempDir) throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, "invalidpassword");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(() -> validatorSource.getAvailableValidators().get(0).createSigner())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage(
            "Failed to decrypt keystore " + scryptKeystore + ". Check the password is correct.");
  }

  @Test
  void shouldThrowExceptionWhenPasswordFileDoesNotExist(@TempDir final Path tempDir)
      throws Exception {
    // load keystores from resources
    final Path scryptKeystore = Path.of(Resources.getResource("scryptTestVector.json").toURI());

    // create password file
    final Path tempPasswordFile = tempDir.resolve("nonexistent.txt");

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Keystore password file not found: " + tempPasswordFile);
  }

  @Test
  void shouldThrowExceptionWhenKeystoreFileDoesNotExist(@TempDir final Path tempDir)
      throws IOException {
    // load keystores from resources
    final Path scryptKeystore = tempDir.resolve("scryptTestVector.json");

    // create password file
    final Path tempPasswordFile = createTempFile(tempDir, "pass", ".txt");
    writeString(tempPasswordFile, EXPECTED_PASSWORD);

    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        List.of(Pair.of(scryptKeystore, tempPasswordFile));

    when(keyStoreFilesLocator.parse()).thenReturn(keystorePasswordFilePairs);

    assertThatThrownBy(validatorSource::getAvailableValidators)
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageStartingWith("KeyStore file not found: file:")
        .hasMessageEndingWith(scryptKeystore.getFileName().toString());
  }

  private void assertProviderMatchesKey(
      final ValidatorProvider provider, final BLSKeyPair expectedKeyPair) {
    assertThat(provider.getPublicKey()).isEqualTo(expectedKeyPair.getPublicKey());
    final Signer signer = provider.createSigner();
    final Bytes4 version = Bytes4.fromHexString("0x00000000");
    final UInt64 epoch = UInt64.ZERO;
    final ForkInfo forkInfo = new ForkInfo(new Fork(version, version, UInt64.ZERO), Bytes32.ZERO);
    final Bytes signingRoot = signingRootUtil.signingRootForRandaoReveal(epoch, forkInfo);

    final SafeFuture<BLSSignature> signingFuture = signer.createRandaoReveal(epoch, forkInfo);
    asyncRunner.executeQueuedActions();
    assertThat(signingFuture).isCompleted();
    final BLSSignature signature = signingFuture.getNow(null);
    assertThat(BLS.verify(expectedKeyPair.getPublicKey(), signingRoot, signature)).isTrue();
  }

  @Test
  void shouldRejectDeletingValidatorIfReadOnlySource() {
    final DeleteKeyResult result =
        validatorSource.deleteValidator(dataStructureUtil.randomPublicKey());
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.ERROR);
    assertThat(result.getMessage().orElse("")).contains("read-only local validator source");
  }

  @Test
  void shouldRejectAddingValidatorIfReadOnlySource() {
    final KeyStoreData keyStoreData = mock(KeyStoreData.class);
    when(keyStoreData.getPubkey()).thenReturn(dataStructureUtil.randomPublicKey().toSSZBytes());
    final AddValidatorResult result =
        validatorSource.addValidator(keyStoreData, "pass", dataStructureUtil.randomPublicKey());
    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(result.getResult().getMessage().orElse("")).contains("read only source");
  }

  @Test
  void shouldAddValidatorIfNotReadOnlySource(@TempDir final Path tempDir) throws IOException {
    final AddValidatorResult result =
        getResultFromAddingValidator(
            tempDir, "pbkdf2TestVector.json", EXPECTED_PASSWORD, PBKDF2_PUBKEY);
    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(result.getSigner()).isNotEmpty();
  }

  @Test
  void shouldDetectDuplicatesOnAddValidator(@TempDir final Path tempDir) throws IOException {
    final AddValidatorResult result =
        getResultFromAddingValidator(
            tempDir, "pbkdf2TestVector.json", EXPECTED_PASSWORD, PBKDF2_PUBKEY);
    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(result.getSigner()).isNotEmpty();
    final AddValidatorResult result2 =
        getResultFromAddingValidator(
            tempDir, "pbkdf2TestVector.json", EXPECTED_PASSWORD, PBKDF2_PUBKEY);
    assertThat(result2.getResult().getImportStatus()).isEqualTo(ImportStatus.DUPLICATE);
    assertThat(result2.getSigner()).isEmpty();
  }

  @Test
  void shouldErrorIfPasswordIsIncorrectOnAddValidator(@TempDir final Path tempDir)
      throws IOException {
    final AddValidatorResult result =
        getResultFromAddingValidator(
            tempDir, "pbkdf2TestVector.json", "zz", dataStructureUtil.randomPublicKey());

    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.ERROR);
    assertThat(result.getResult().getMessage().orElse("")).contains("password");
    assertThat(result.getSigner()).isEmpty();
  }

  @Test
  void shouldSayFalseToAddValidators() {
    assertThat(validatorSource.canUpdateValidators()).isFalse();
  }

  private AddValidatorResult getResultFromAddingValidator(
      final Path tempDir,
      final String resourceName,
      final String password,
      final BLSPublicKey publicKey)
      throws IOException {
    final LocalValidatorSource localValidatorSource =
        new LocalValidatorSource(
            spec,
            true,
            keystoreLocker,
            keyStoreFilesLocator,
            asyncRunner,
            false,
            Optional.of(new SimpleDataDirLayout(tempDir)));
    final KeyStoreData keyStoreData =
        KeyStoreLoader.loadFromString(
            Resources.toString(Resources.getResource(resourceName), StandardCharsets.UTF_8));
    return localValidatorSource.addValidator(keyStoreData, password, publicKey);
  }
}
