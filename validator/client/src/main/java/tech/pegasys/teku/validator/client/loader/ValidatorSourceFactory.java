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

import java.io.File;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientService;

/**
 * ValidatorSourceFactory creates the validator sources that control loading, and sometimes removal,
 * of validators in memory.
 *
 * <p><a href="https://ethereum.github.io/keymanager-APIs/">Keymanager API</a> Validators added via
 * keymanager are considered not read-only, they can be added and removed via API calls at runtime,
 * without the need to issue HUP signals.
 *
 * <p>Read-only sources can include sources that are loaded from CLI options like `--validator-keys`
 * and `--validators-external-signer-public-keys`, where a static list of validators can be
 * specified and re-loaded during a HUP or on every restart.
 *
 * <p>Removal of mutable sources makes use of a delegation pattern, where the `DeletableSigner`
 * ensures that signing cannot occur once the source has been marked deleted.
 *
 * <p>DeletableSigner -> SlashingProtectedSigner -> (LocalSigner | ExternalSigner)
 *
 * <ul>
 *   <li>DeletableSigner being at the top of the hierarchy ensures that a signer can be deleted and
 *       ensure no further signing occurs. Outstanding duties for a validator may still attempt to
 *       process. It also delegates if it is read-only: read-only signers will not be allowed to be
 *       deleted.
 *   <li>SlashingProtectedSigner ensures that a signer cannot attempt to sign anything that may
 *       violate slashing conditions. Actual signing is delegated once it has been verified that
 *       slashing would not occur.
 *   <li>LocalSigner uses local signing
 *   <li>ExternalSigner delegates singing to an external tool
 * </ul>
 */
public class ValidatorSourceFactory {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final ValidatorConfig config;
  private final InteropConfig interopConfig;
  private final Supplier<HttpClient> externalSignerHttpClientFactory;
  private final SlashingProtector slashingProtector;
  private final PublicKeyLoader publicKeyLoader;
  private final AsyncRunner asyncRunner;
  private final MetricsSystem metricsSystem;
  private final Optional<DataDirLayout> maybeDataDir;
  private Optional<ValidatorSource> mutableLocalValidatorSource = Optional.empty();
  private Optional<ValidatorSource> mutableExternalValidatorSource = Optional.empty();
  private ThrottlingTaskQueueWithPriority externalSignerTaskQueue;

  public ValidatorSourceFactory(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeDataDir) {
    this.spec = spec;
    this.config = config;
    this.interopConfig = interopConfig;
    this.externalSignerHttpClientFactory = externalSignerHttpClientFactory;
    this.slashingProtector = slashingProtector;
    this.publicKeyLoader = publicKeyLoader;
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.maybeDataDir = maybeDataDir;
  }

  public List<ValidatorSource> createValidatorSources() {
    final List<ValidatorSource> validatorSources = new ArrayList<>();
    if (interopConfig.isInteropEnabled()) {
      validatorSources.add(slashingProtected(addInteropValidatorSource()));
    } else {
      addExternalValidatorSource().ifPresent(validatorSources::add);
      addLocalValidatorSource().ifPresent(validatorSources::add);
      addMutableLocalValidatorSource().ifPresent(validatorSources::add);
      addMutableExternalValidatorSource().ifPresent(validatorSources::add);
    }
    return validatorSources;
  }

  public Optional<ValidatorSource> getMutableLocalValidatorSource() {
    return mutableLocalValidatorSource;
  }

  public Optional<ValidatorSource> getMutableExternalValidatorSource() {
    return mutableExternalValidatorSource;
  }

  private Optional<ValidatorSource> addMutableLocalValidatorSource() {
    if (maybeDataDir.isEmpty()) {
      return Optional.empty();
    }
    final DataDirLayout dataDirLayout = maybeDataDir.get();
    final Path keystorePath = ValidatorClientService.getManagedLocalKeystorePath(dataDirLayout);
    final Path keystorePasswordPath =
        ValidatorClientService.getManagedLocalKeystorePasswordPath(dataDirLayout);

    if (!ensurePathExists(keystorePath) || !ensurePathExists(keystorePasswordPath)) {
      LOG.error("Could not initialise mutable paths, mutable storage will not be available");
      return Optional.empty();
    }

    final KeyStoreFilesLocator keyStoreFilesLocator =
        new KeyStoreFilesLocator(
            List.of(keystorePath + File.pathSeparator + keystorePasswordPath), File.pathSeparator);

    final LocalValidatorSource localValidatorSource =
        new LocalValidatorSource(
            spec,
            config.isValidatorKeystoreLockingEnabled(),
            new KeystoreLocker(),
            keyStoreFilesLocator,
            asyncRunner,
            false,
            maybeDataDir);
    mutableLocalValidatorSource = Optional.of(slashingProtected(localValidatorSource));
    return mutableLocalValidatorSource;
  }

  private ValidatorSource addInteropValidatorSource() {
    final MockStartValidatorSource.Builder builder =
        new MockStartValidatorSource.Builder(spec, interopConfig, asyncRunner);
    if (config.getValidatorExternalSignerUrl() == null) {
      return builder.useExternalSigner(false).build();
    }

    return builder
        .useExternalSigner(true)
        .config(config)
        .externalSignerHttpClientFactory(externalSignerHttpClientFactory)
        .metricsSystem(metricsSystem)
        .externalSignerTaskQueue(initializeExternalSignerTaskQueue())
        .build();
  }

  private Optional<ValidatorSource> addMutableExternalValidatorSource() {
    if (config.getValidatorExternalSignerUrl() == null) {
      return Optional.empty();
    }

    final ExternalValidatorSource externalValidatorSource =
        ExternalValidatorSource.create(
            spec,
            metricsSystem,
            config,
            externalSignerHttpClientFactory,
            publicKeyLoader,
            asyncRunner,
            false,
            initializeExternalSignerTaskQueue(),
            maybeDataDir);
    mutableExternalValidatorSource = Optional.of(slashingProtected(externalValidatorSource));
    return mutableExternalValidatorSource;
  }

  private boolean ensurePathExists(final Path directory) {
    return directory.toFile().exists() || directory.toFile().mkdirs();
  }

  private Optional<ValidatorSource> addLocalValidatorSource() {
    if (config.getValidatorKeys() == null) {
      return Optional.empty();
    }
    KeyStoreFilesLocator keyStoreFilesLocator =
        new KeyStoreFilesLocator(config.getValidatorKeys(), File.pathSeparator);
    return Optional.of(
        slashingProtected(
            new LocalValidatorSource(
                spec,
                config.isValidatorKeystoreLockingEnabled(),
                new KeystoreLocker(),
                keyStoreFilesLocator,
                asyncRunner,
                true,
                maybeDataDir)));
  }

  private Optional<ValidatorSource> addExternalValidatorSource() {
    if (config.getValidatorExternalSignerPublicKeySources().isEmpty()) {
      return Optional.empty();
    }

    final ValidatorSource externalValidatorSource =
        ExternalValidatorSource.create(
            spec,
            metricsSystem,
            config,
            externalSignerHttpClientFactory,
            publicKeyLoader,
            asyncRunner,
            true,
            initializeExternalSignerTaskQueue(),
            maybeDataDir);
    return Optional.of(
        config.isValidatorExternalSignerSlashingProtectionEnabled()
            ? slashingProtected(externalValidatorSource)
            : externalValidatorSource);
  }

  private ValidatorSource slashingProtected(final ValidatorSource validatorSource) {
    return new SlashingProtectedValidatorSource(validatorSource, slashingProtector);
  }

  private ThrottlingTaskQueueWithPriority initializeExternalSignerTaskQueue() {
    if (externalSignerTaskQueue == null) {
      externalSignerTaskQueue =
          ThrottlingTaskQueueWithPriority.create(
              config.getValidatorExternalSignerConcurrentRequestLimit(),
              ThrottlingTaskQueue.DEFAULT_MAXIMUM_QUEUE_SIZE,
              metricsSystem,
              TekuMetricCategory.VALIDATOR,
              "external_signer_request_queue_size",
              "external_signer_request_queue_rejected");
    }

    return externalSignerTaskQueue;
  }
}
