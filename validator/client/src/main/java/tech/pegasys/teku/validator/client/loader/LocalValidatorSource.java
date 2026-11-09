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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class LocalValidatorSource extends AbstractValidatorSource implements ValidatorSource {

  private final Spec spec;
  private final boolean validatorKeystoreLockingEnabled;
  private final KeystoreLocker keystoreLocker;
  private final AsyncRunner asyncRunner;
  private final KeyStoreFilesLocator keyStoreFilesLocator;
  private final Map<BLSPublicKey, ActiveLocalValidatorSource> localValidatorSourceMap =
      new ConcurrentHashMap<>();

  public LocalValidatorSource(
      final Spec spec,
      final boolean validatorKeystoreLockingEnabled,
      final KeystoreLocker keystoreLocker,
      final KeyStoreFilesLocator keyStoreFilesLocator,
      final AsyncRunner asyncRunner,
      final boolean readOnly,
      final Optional<DataDirLayout> maybeDataDirLayout) {
    super(readOnly, maybeDataDirLayout);
    this.spec = spec;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.keystoreLocker = keystoreLocker;
    this.asyncRunner = asyncRunner;
    this.keyStoreFilesLocator = keyStoreFilesLocator;
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    final List<Pair<Path, Path>> filePairs = keyStoreFilesLocator.parse();
    return filePairs.stream().map(this::createValidatorProvider).toList();
  }

  @Override
  public DeleteKeyResult deleteValidator(final BLSPublicKey publicKey) {
    if (!canUpdateValidators()) {
      return DeleteKeyResult.error(
          "Cannot delete validator from read-only local validator source.");
    }
    final ActiveLocalValidatorSource source = localValidatorSourceMap.remove(publicKey);
    if (source == null) {
      return DeleteKeyResult.error(
          "Could not find " + publicKey.toBytesCompressed().toShortHexString() + " to delete");
    }
    final DeleteKeyResult result = source.delete();
    if (result.getStatus() == DeletionStatus.DELETED) {
      keystoreLocker.unlockKeystore(getKeystorePath(publicKey));
    }
    return result;
  }

  @Override
  public AddValidatorResult addValidator(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    if (!canUpdateValidators()) {
      return new AddValidatorResult(
          PostKeyResult.error("Cannot add validator to a read only source."), Optional.empty());
    }

    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final Path passwordPath = getPasswordPath(publicKey);
    final Path keystorePath = getKeystorePath(publicKey);
    try {
      ensureDirectoryExists(
          ValidatorClientService.getManagedLocalKeystorePasswordPath(dataDirLayout));
      ensureDirectoryExists(ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout));

      if (passwordPath.toFile().exists() || keystorePath.toFile().exists()) {
        return new AddValidatorResult(PostKeyResult.duplicate(), Optional.empty());
      }
      Files.write(passwordPath, password.getBytes(UTF_8));
      KeyStoreLoader.saveToFile(keystorePath, keyStoreData);
      final ValidatorProvider provider =
          new LocalValidatorProvider(
              spec, keyStoreData, keystorePath, publicKey, password, readOnly);
      localValidatorSourceMap.put(
          publicKey, new ActiveLocalValidatorSource(keystorePath, passwordPath));
      return new AddValidatorResult(PostKeyResult.success(), Optional.of(provider.createSigner()));
    } catch (InvalidConfigurationException | IOException ex) {
      cleanupIncompleteSave(keystorePath);
      cleanupIncompleteSave(passwordPath);
      keystoreLocker.unlockKeystore(keystorePath);
      return new AddValidatorResult(PostKeyResult.error(ex.getMessage()), Optional.empty());
    }
  }

  @Override
  public AddValidatorResult addValidator(
      final BLSPublicKey publicKey, final Optional<URL> signerUrl) {
    throw new UnsupportedOperationException();
  }

  private Path getKeystorePath(final BLSPublicKey publicKey) {
    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final String fileName = publicKey.toBytesCompressed().toUnprefixedHexString();
    return ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout)
        .resolve(fileName + ".json");
  }

  private Path getPasswordPath(final BLSPublicKey publicKey) {
    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final String fileName = publicKey.toBytesCompressed().toUnprefixedHexString();
    return ValidatorClientService.getManagedLocalKeystorePasswordPath(dataDirLayout)
        .resolve(fileName + ".txt");
  }

  ValidatorProvider createValidatorProvider(final Pair<Path, Path> keystorePasswordFilePair) {
    final Path keystorePath = keystorePasswordFilePair.getLeft();
    final Path passwordPath = keystorePasswordFilePair.getRight();
    try {
      final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystorePath.toUri());
      final BLSPublicKey publicKey =
          BLSPublicKey.fromBytesCompressedValidate(Bytes48.wrap(keyStoreData.getPubkey()));
      final String password = loadPassword(passwordPath);
      localValidatorSourceMap.put(
          publicKey, new ActiveLocalValidatorSource(keystorePath, passwordPath));
      return new LocalValidatorProvider(
          spec, keyStoreData, keystorePath, publicKey, password, readOnly);
    } catch (final KeyStoreValidationException e) {
      if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
        throw new InvalidConfigurationException(e.getMessage(), e);
      }
      throw new InvalidConfigurationException("Invalid keystore: " + keystorePath, e);
    }
  }

  private String loadPassword(final Path passwordFile) {
    final String password;
    try {
      password = Files.readString(passwordFile, UTF_8);
      if (isEmpty(password)) {
        throw new InvalidConfigurationException(
            "Keystore password cannot be empty: " + passwordFile);
      }
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throw new InvalidConfigurationException(
          "Keystore password file not found: " + passwordFile, e);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Unexpected IO error while reading keystore password file [%s]: %s",
              passwordFile, e.getMessage());
      throw new InvalidConfigurationException(errorMessage, e);
    }
    return password;
  }

  private class LocalValidatorProvider implements ValidatorProvider {

    private final Spec spec;
    private final KeyStoreData keyStoreData;
    private final Path keystoreFile;
    private final BLSPublicKey publicKey;
    private final String password;
    private final boolean readOnly;

    private LocalValidatorProvider(
        final Spec spec,
        final KeyStoreData keyStoreData,
        final Path keystoreFile,
        final BLSPublicKey publicKey,
        final String password,
        final boolean readOnly) {
      this.spec = spec;
      this.keyStoreData = keyStoreData;
      this.keystoreFile = keystoreFile;
      this.publicKey = publicKey;
      this.password = password;
      this.readOnly = readOnly;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return publicKey;
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    @Override
    public Signer createSigner() {
      final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(loadBLSPrivateKey()));
      if (!keyPair.getPublicKey().equals(getPublicKey())) {
        throw new InvalidConfigurationException(
            String.format(
                "Keystore declares incorrect public key. Was %s but expected %s",
                getPublicKey(), keyPair.getPublicKey()));
      }
      return new LocalSigner(spec, keyPair, asyncRunner);
    }

    private Bytes32 loadBLSPrivateKey() {
      try {
        if (validatorKeystoreLockingEnabled) {
          keystoreLocker.lockKeystore(keystoreFile);
        }
        return Bytes32.wrap(KeyStore.decrypt(password, keyStoreData));
      } catch (final KeyStoreValidationException e) {
        keystoreLocker.unlockKeystore(keystoreFile);
        throw new InvalidConfigurationException(
            "Failed to decrypt keystore " + keystoreFile + ". Check the password is correct.", e);
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("keystoreFile", keystoreFile)
          .add("publicKey", publicKey)
          .toString();
    }
  }
}
