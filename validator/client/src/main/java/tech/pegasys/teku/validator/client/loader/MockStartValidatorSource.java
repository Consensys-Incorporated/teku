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

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;

public class MockStartValidatorSource implements ValidatorSource {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final InteropConfig interopConfig;
  private final AsyncRunner asyncRunner;
  private final boolean useExternalSigner;

  // used by external signer
  private final ValidatorConfig config;
  private final Supplier<HttpClient> externalSignerHttpClientFactory;
  private final ThrottlingTaskQueueWithPriority externalSignerTaskQueue;
  private final MetricsSystem metricsSystem;

  private MockStartValidatorSource(final Builder builder) {
    this.spec = builder.spec;
    this.interopConfig = builder.interopConfig;
    this.asyncRunner = builder.asyncRunner;
    this.useExternalSigner = builder.useExternalSigner;
    this.config = builder.config;
    this.externalSignerHttpClientFactory = builder.externalSignerHttpClientFactory;
    this.externalSignerTaskQueue = builder.externalSignerTaskQueue;
    this.metricsSystem = builder.metricsSystem;
  }

  @Override
  public List<? extends ValidatorProvider> getAvailableValidators() {
    final int startIndex = interopConfig.getInteropOwnedValidatorStartIndex();
    final int endIndex = startIndex + interopConfig.getInteropOwnedValidatorCount();
    LOG.log(
        Level.DEBUG,
        "Owning validator range {} to {} using {} signer",
        startIndex,
        endIndex,
        useExternalSigner ? "External" : "Local");
    return new MockStartValidatorKeyPairFactory()
        .generateKeyPairs(startIndex, endIndex).stream().map(this::getValidatorProvider).toList();
  }

  private ValidatorProvider getValidatorProvider(final BLSKeyPair keyPair) {
    if (!useExternalSigner) {
      return new MockStartValidatorProvider(keyPair);
    }

    return new ExternalValidatorProvider(
        spec,
        externalSignerHttpClientFactory,
        config.getValidatorExternalSignerUrl(),
        keyPair.getPublicKey(),
        config.getValidatorExternalSignerTimeout(),
        externalSignerTaskQueue,
        metricsSystem,
        true);
  }

  @Override
  public boolean canUpdateValidators() {
    return false;
  }

  @Override
  public DeleteKeyResult deleteValidator(final BLSPublicKey publicKey) {
    throw new UnsupportedOperationException("Cannot delete validator from mock validator source.");
  }

  @Override
  public AddValidatorResult addValidator(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AddValidatorResult addValidator(
      final BLSPublicKey publicKey, final Optional<URL> signerUrl) {
    throw new UnsupportedOperationException();
  }

  private class MockStartValidatorProvider implements ValidatorProvider {
    private final BLSKeyPair keyPair;

    private MockStartValidatorProvider(final BLSKeyPair keyPair) {
      this.keyPair = keyPair;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return keyPair.getPublicKey();
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }

    @Override
    public Signer createSigner() {
      return new LocalSigner(spec, keyPair, asyncRunner);
    }
  }

  public static class Builder {
    private final Spec spec;
    private final InteropConfig interopConfig;
    private final AsyncRunner asyncRunner;
    private boolean useExternalSigner = false;
    private ValidatorConfig config;
    private Supplier<HttpClient> externalSignerHttpClientFactory;
    private ThrottlingTaskQueueWithPriority externalSignerTaskQueue;
    private MetricsSystem metricsSystem;

    public Builder(
        final Spec spec, final InteropConfig interopConfig, final AsyncRunner asyncRunner) {
      this.spec = spec;
      this.interopConfig = interopConfig;
      this.asyncRunner = asyncRunner;
    }

    public Builder useExternalSigner(final boolean useExternalSigner) {
      this.useExternalSigner = useExternalSigner;
      return this;
    }

    public Builder config(final ValidatorConfig config) {
      this.config = config;
      return this;
    }

    public Builder externalSignerHttpClientFactory(
        final Supplier<HttpClient> externalSignerHttpClientFactory) {
      this.externalSignerHttpClientFactory = externalSignerHttpClientFactory;
      return this;
    }

    public Builder externalSignerTaskQueue(
        final ThrottlingTaskQueueWithPriority externalSignerTaskQueue) {
      this.externalSignerTaskQueue = externalSignerTaskQueue;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      this.metricsSystem = metricsSystem;
      return this;
    }

    public MockStartValidatorSource build() {
      if (useExternalSigner) {
        checkNotNull(config);
        checkNotNull(externalSignerHttpClientFactory);
        checkNotNull(externalSignerTaskQueue);
        checkNotNull(metricsSystem);
      }
      return new MockStartValidatorSource(this);
    }
  }
}
