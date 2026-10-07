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

package tech.pegasys.teku.validator.client.loader;

import com.google.common.annotations.VisibleForTesting;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.DeletableSigner;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.UpdatableGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ExternalValidatorImportResult;
import tech.pegasys.teku.validator.client.LocalValidatorImportResult;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ValidatorLoader {

  private static final Logger LOG = LogManager.getLogger();
  private final List<ValidatorSource> validatorSources;
  private final Optional<ValidatorSource> mutableLocalValidatorSource;
  private final Optional<ValidatorSource> mutableExternalValidatorSource;
  private final OwnedValidators ownedValidators = new OwnedValidators();
  private final GraffitiProvider defaultGraffitiProvider;
  private final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider;
  private final Optional<DataDirLayout> maybeDataDirLayout;
  private final SlashingProtectionLogger slashingProtectionLogger;
  private final SlashingProtector slashingProtector;

  private ValidatorLoader(
      final List<ValidatorSource> validatorSources,
      final Optional<ValidatorSource> mutableLocalValidatorSource,
      final Optional<ValidatorSource> mutableExternalValidatorSource,
      final GraffitiProvider defaultGraffitiProvider,
      final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider,
      final Optional<DataDirLayout> maybeDataDirLayout,
      final SlashingProtectionLogger slashingProtectionLogger,
      final SlashingProtector slashingProtector) {
    this.validatorSources = validatorSources;
    this.mutableLocalValidatorSource = mutableLocalValidatorSource;
    this.mutableExternalValidatorSource = mutableExternalValidatorSource;
    this.defaultGraffitiProvider = defaultGraffitiProvider;
    this.updatableGraffitiProvider = updatableGraffitiProvider;
    this.maybeDataDirLayout = maybeDataDirLayout;
    this.slashingProtectionLogger = slashingProtectionLogger;
    this.slashingProtector = slashingProtector;
  }

  // synchronized to ensure that only one load is active at a time
  public synchronized void loadValidators() {
    final Map<BLSPublicKey, ValidatorProvider> validatorProviders = new HashMap<>();
    validatorSources.forEach(source -> addValidatorsFromSource(validatorProviders, source));
    MultithreadedValidatorLoader.loadValidators(
        ownedValidators,
        validatorProviders,
        defaultGraffitiProvider,
        updatableGraffitiProvider,
        maybeDataDirLayout);
    slashingProtectionLogger.protectionSummary(ownedValidators.getValidators());
  }

  public DeleteKeyResult deleteLocalMutableValidator(final BLSPublicKey publicKey) {
    if (mutableLocalValidatorSource.isEmpty()) {
      return DeleteKeyResult.error(
          "Unable to delete validator, could not determine the storage location.");
    }
    return mutableLocalValidatorSource.get().deleteValidator(publicKey);
  }

  public synchronized LocalValidatorImportResult loadLocalMutableValidator(
      final KeyStoreData keyStoreData,
      final String password,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter,
      final boolean addToOwnedValidators) {

    if (!canAddValidator(mutableLocalValidatorSource)) {
      return new LocalValidatorImportResult.Builder(
              PostKeyResult.error("Not able to add validator"), password)
          .keyStoreData(Optional.ofNullable(keyStoreData))
          .build();
    }

    final BLSPublicKey publicKey =
        BLSPublicKey.fromBytesCompressed(Bytes48.wrap(keyStoreData.getPubkey()));

    if (slashingProtectionImporter.isPresent()) {
      final Optional<String> errorString =
          slashingProtector.importSigningRecord(
              publicKey,
              () -> slashingProtectionImporter.get().updateSigningRecord(publicKey, LOG::debug));
      if (errorString.isPresent()) {
        return new LocalValidatorImportResult.Builder(
                PostKeyResult.error(errorString.get()), password)
            .publicKey(Optional.of(publicKey))
            .keyStoreData(Optional.of(keyStoreData))
            .build();
      }
    }
    if (ownedValidators.hasValidator(publicKey)) {
      return new LocalValidatorImportResult.Builder(PostKeyResult.duplicate(), password)
          .publicKey(Optional.of(publicKey))
          .keyStoreData(Optional.of(keyStoreData))
          .build();
    }

    if (addToOwnedValidators) {
      return addValidator(keyStoreData, password, publicKey);
    } else {
      return checkKeystorePassword(keyStoreData, password, publicKey);
    }
  }

  private LocalValidatorImportResult checkKeystorePassword(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    final boolean isCorrectKeystorePassword = KeyStore.validatePassword(password, keyStoreData);
    final PostKeyResult result =
        isCorrectKeystorePassword
            ? PostKeyResult.success()
            : PostKeyResult.error(
                "Invalid keystore password for public key: " + keyStoreData.getAbbreviatedPubKey());
    return new LocalValidatorImportResult.Builder(result, password)
        .publicKey(Optional.of(publicKey))
        .keyStoreData(Optional.of(keyStoreData))
        .build();
  }

  public LocalValidatorImportResult addValidator(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    final AddValidatorResult validatorAddResult =
        mutableLocalValidatorSource.get().addValidator(keyStoreData, password, publicKey);
    if (validatorAddResult.getSigner().isEmpty()) {
      return new LocalValidatorImportResult.Builder(validatorAddResult.getResult(), password)
          .publicKey(Optional.of(publicKey))
          .keyStoreData(Optional.of(keyStoreData))
          .build();
    }
    addToOwnedValidators(validatorAddResult.getSigner().get(), publicKey);
    return new LocalValidatorImportResult.Builder(PostKeyResult.success(), password)
        .publicKey(Optional.of(publicKey))
        .keyStoreData(Optional.of(keyStoreData))
        .build();
  }

  public DeleteKeyResult deleteExternalMutableValidator(final BLSPublicKey publicKey) {
    if (mutableExternalValidatorSource.isEmpty()) {
      return DeleteKeyResult.error(
          "Unable to delete external validator, could not determine the storage location.");
    }
    return mutableExternalValidatorSource.get().deleteValidator(publicKey);
  }

  public synchronized ExternalValidatorImportResult loadExternalMutableValidator(
      final BLSPublicKey publicKey,
      final Optional<URL> signerUrl,
      final boolean addToOwnedValidators) {
    if (!canAddValidator(mutableExternalValidatorSource)) {
      return new ExternalValidatorImportResult.Builder(
              PostKeyResult.error("Not able to add validator"), signerUrl)
          .publicKey(Optional.of(publicKey))
          .build();
    }
    if (ownedValidators.hasValidator(publicKey)) {
      return new ExternalValidatorImportResult.Builder(PostKeyResult.duplicate(), signerUrl)
          .publicKey(Optional.of(publicKey))
          .build();
    }

    if (addToOwnedValidators) {
      return addExternalValidator(signerUrl, publicKey);
    }
    return new ExternalValidatorImportResult.Builder(PostKeyResult.success(), signerUrl)
        .publicKey(Optional.of(publicKey))
        .build();
  }

  public ExternalValidatorImportResult addExternalValidator(
      final Optional<URL> signerUrl, final BLSPublicKey publicKey) {
    final AddValidatorResult validatorAddResult =
        mutableExternalValidatorSource.get().addValidator(publicKey, signerUrl);

    if (validatorAddResult.getSigner().isEmpty()) {
      return new ExternalValidatorImportResult.Builder(validatorAddResult.getResult(), signerUrl)
          .publicKey(Optional.of(publicKey))
          .build();
    }
    addToOwnedValidators(validatorAddResult.getSigner().get(), publicKey);
    return new ExternalValidatorImportResult.Builder(
            PostKeyResult.success(), validatorAddResult.getSigner().get().getSigningServiceUrl())
        .publicKey(Optional.of(publicKey))
        .build();
  }

  private void addToOwnedValidators(final Signer signer, final BLSPublicKey publicKey) {
    final GraffitiProvider graffitiProvider =
        maybeDataDirLayout
            .<GraffitiProvider>map(
                dataDirLayout ->
                    new UpdatableGraffitiProvider(
                        () -> updatableGraffitiProvider.apply(publicKey), defaultGraffitiProvider))
            .orElse(defaultGraffitiProvider);
    ownedValidators.addValidator(
        new Validator(publicKey, new DeletableSigner(signer), graffitiProvider, false));
    LOG.info("Added validator: {}", publicKey.toString());
  }

  private boolean canAddValidator(final Optional<ValidatorSource> validatorSource) {
    return validatorSource.isPresent()
        && validatorSource.get().canUpdateValidators()
        && maybeDataDirLayout.isPresent();
  }

  public OwnedValidators getOwnedValidators() {
    return ownedValidators;
  }

  public static ValidatorLoader create(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final SlashingProtector slashingProtector,
      final SlashingProtectionLogger slashingProtectionLogger,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeMutableDir,
      final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider) {
    final ValidatorSourceFactory validatorSources =
        new ValidatorSourceFactory(
            spec,
            config,
            interopConfig,
            externalSignerHttpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            maybeMutableDir);

    final List<ValidatorSource> validatorSourceList = validatorSources.createValidatorSources();
    return new ValidatorLoader(
        validatorSourceList,
        validatorSources.getMutableLocalValidatorSource(),
        validatorSources.getMutableExternalValidatorSource(),
        config.getGraffitiProvider(),
        updatableGraffitiProvider,
        maybeMutableDir,
        slashingProtectionLogger,
        slashingProtector);
  }

  @VisibleForTesting
  static ValidatorLoader create(
      final List<ValidatorSource> validatorSources,
      final Optional<ValidatorSource> mutableLocalValidatorSource,
      final Optional<ValidatorSource> mutableExternalValidatorSource,
      final GraffitiProvider graffitiProvider,
      final Optional<DataDirLayout> maybeDataDirLayout,
      final SlashingProtectionLogger slashingProtectionLogger,
      final SlashingProtector slashingProtector) {
    return new ValidatorLoader(
        validatorSources,
        mutableLocalValidatorSource,
        mutableExternalValidatorSource,
        graffitiProvider,
        (publicKey) -> Optional.empty(),
        maybeDataDirLayout,
        slashingProtectionLogger,
        slashingProtector);
  }

  private void addValidatorsFromSource(
      final Map<BLSPublicKey, ValidatorProvider> validators, final ValidatorSource source) {
    source.getAvailableValidators().stream()
        .filter(provider -> !ownedValidators.hasValidator(provider.getPublicKey()))
        .forEach(
            validatorProvider ->
                validators.putIfAbsent(validatorProvider.getPublicKey(), validatorProvider));
  }

  public SlashingProtectionLogger getSlashingProtectionLogger() {
    return slashingProtectionLogger;
  }
}
