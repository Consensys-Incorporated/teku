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
import static tech.pegasys.teku.infrastructure.json.JsonUtil.parse;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.signer.ExternalSignerStatusLogger;
import tech.pegasys.teku.validator.client.signer.ExternalSignerUpcheck;

public class ExternalValidatorSource extends AbstractValidatorSource implements ValidatorSource {

  private final Spec spec;
  private final ValidatorConfig config;
  private final Supplier<HttpClient> externalSignerHttpClientFactory;
  private final PublicKeyLoader publicKeyLoader;
  private final ThrottlingTaskQueueWithPriority externalSignerTaskQueue;
  private final MetricsSystem metricsSystem;
  private final Map<BLSPublicKey, URL> externalValidatorSourceMap = new ConcurrentHashMap<>();

  private ExternalValidatorSource(
      final Spec spec,
      final ValidatorConfig config,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final PublicKeyLoader publicKeyLoader,
      final ThrottlingTaskQueueWithPriority externalSignerTaskQueue,
      final MetricsSystem metricsSystem,
      final boolean readOnly,
      final Optional<DataDirLayout> maybeDataDirLayout) {
    super(readOnly, maybeDataDirLayout);
    this.spec = spec;
    this.config = config;
    this.externalSignerHttpClientFactory = externalSignerHttpClientFactory;
    this.publicKeyLoader = publicKeyLoader;
    this.externalSignerTaskQueue = externalSignerTaskQueue;
    this.metricsSystem = metricsSystem;
  }

  public static ExternalValidatorSource create(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final ValidatorConfig config,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final boolean readOnly,
      final ThrottlingTaskQueueWithPriority externalSignerTaskQueue,
      final Optional<DataDirLayout> maybeDataDirLayout) {
    setupExternalSignerStatusLogging(config, externalSignerHttpClientFactory, asyncRunner);
    return new ExternalValidatorSource(
        spec,
        config,
        externalSignerHttpClientFactory,
        publicKeyLoader,
        externalSignerTaskQueue,
        metricsSystem,
        readOnly,
        maybeDataDirLayout);
  }

  @Override
  public List<? extends ValidatorProvider> getAvailableValidators() {
    if (readOnly) {
      return getAvailableReadOnlyValidators();
    }

    // Load from files
    final List<File> files = getValidatorFiles();
    return files.stream().map(this::getValidatorProvider).toList();
  }

  private List<? extends ValidatorProvider> getAvailableReadOnlyValidators() {
    final List<BLSPublicKey> publicKeys =
        publicKeyLoader.getPublicKeys(config.getValidatorExternalSignerPublicKeySources());
    return publicKeys.stream()
        .map(
            key ->
                new ExternalValidatorProvider(
                    spec,
                    externalSignerHttpClientFactory,
                    config.getValidatorExternalSignerUrl(),
                    key,
                    config.getValidatorExternalSignerTimeout(),
                    externalSignerTaskQueue,
                    metricsSystem,
                    readOnly))
        .toList();
  }

  private List<File> getValidatorFiles() {
    if (maybeDataDirLayout.isEmpty()) {
      return List.of();
    }

    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final Path directory = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);

    final File[] files =
        directory.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith("json"));
    return files == null ? List.of() : Arrays.asList(files);
  }

  private ValidatorProvider getValidatorProvider(final File file) {
    try {
      String content = Files.readString(file.toPath());
      ExternalValidator externalValidator = parse(content, ValidatorTypes.EXTERNAL_VALIDATOR_STORE);
      URL externalSignerUrl =
          externalValidator.getUrl().orElse(config.getValidatorExternalSignerUrl());

      externalValidatorSourceMap.put(externalValidator.getPublicKey(), externalSignerUrl);
      return new ExternalValidatorProvider(
          spec,
          externalSignerHttpClientFactory,
          externalSignerUrl,
          externalValidator.getPublicKey(),
          config.getValidatorExternalSignerTimeout(),
          externalSignerTaskQueue,
          metricsSystem,
          readOnly);

    } catch (IOException e) {
      throw new InvalidConfigurationException(e.getMessage(), e);
    }
  }

  @Override
  public DeleteKeyResult deleteValidator(final BLSPublicKey publicKey) {
    if (!canUpdateValidators()) {
      return DeleteKeyResult.error(
          "Cannot delete validator from read-only external validator source.");
    }

    if (!externalValidatorSourceMap.containsKey(publicKey)) {
      return DeleteKeyResult.notFound();
    }

    return delete(publicKey);
  }

  private DeleteKeyResult delete(final BLSPublicKey publicKey) {
    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final String fileName = publicKey.toBytesCompressed().toUnprefixedHexString();
    final Path path =
        ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout).resolve(fileName + ".json");
    try {
      ensureDirectoryExists(ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout));
      Files.delete(path);
      externalValidatorSourceMap.remove(publicKey);
      return DeleteKeyResult.success();

    } catch (IOException e) {
      return DeleteKeyResult.error(e.toString());
    }
  }

  @Override
  public AddValidatorResult addValidator(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AddValidatorResult addValidator(
      final BLSPublicKey publicKey, final Optional<URL> signerUrl) {
    if (!canUpdateValidators()) {
      return new AddValidatorResult(
          PostKeyResult.error("Cannot add validator to a read only source."), Optional.empty());
    }

    final DataDirLayout dataDirLayout = maybeDataDirLayout.orElseThrow();
    final String fileName = publicKey.toBytesCompressed().toUnprefixedHexString();
    final Path path =
        ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout).resolve(fileName + ".json");

    try {
      ensureDirectoryExists(ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout));

      if (path.toFile().exists()) {
        return new AddValidatorResult(PostKeyResult.duplicate(), Optional.empty());
      }

      Files.write(
          path,
          serialize(
                  new ExternalValidator(publicKey, signerUrl),
                  ValidatorTypes.EXTERNAL_VALIDATOR_STORE)
              .getBytes(UTF_8));

      final URL url = signerUrl.orElse(config.getValidatorExternalSignerUrl());
      final ValidatorProvider provider =
          new ExternalValidatorProvider(
              spec,
              externalSignerHttpClientFactory,
              url,
              publicKey,
              config.getValidatorExternalSignerTimeout(),
              externalSignerTaskQueue,
              metricsSystem,
              readOnly);

      externalValidatorSourceMap.put(publicKey, url);
      return new AddValidatorResult(PostKeyResult.success(), Optional.of(provider.createSigner()));

    } catch (InvalidConfigurationException | IOException ex) {
      cleanupIncompleteSave(path);
      return new AddValidatorResult(PostKeyResult.error(ex.getMessage()), Optional.empty());
    }
  }

  private static void setupExternalSignerStatusLogging(
      final ValidatorConfig config,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final AsyncRunner asyncRunner) {
    final ExternalSignerUpcheck externalSignerUpcheck =
        new ExternalSignerUpcheck(
            externalSignerHttpClientFactory.get(),
            config.getValidatorExternalSignerUrl(),
            config.getValidatorExternalSignerTimeout());
    final ExternalSignerStatusLogger externalSignerStatusLogger =
        new ExternalSignerStatusLogger(
            STATUS_LOG,
            externalSignerUpcheck::upcheck,
            config.getValidatorExternalSignerUrl(),
            asyncRunner);
    // initial status log
    externalSignerStatusLogger.log();
    // recurring status log
    externalSignerStatusLogger.logWithFixedDelay();
  }
}
