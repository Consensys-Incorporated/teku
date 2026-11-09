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

import com.google.common.annotations.VisibleForTesting;
import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;

class ExternalValidatorProvider implements ValidatorSource.ValidatorProvider {

  private final Spec spec;
  private final Supplier<HttpClient> externalSignerHttpClientFactory;
  private final URL externalSignerUrl;
  private final BLSPublicKey publicKey;
  private final Duration externalSignerTimeout;
  private final ThrottlingTaskQueueWithPriority externalSignerTaskQueue;
  private final MetricsSystem metricsSystem;
  private final boolean readOnly;

  ExternalValidatorProvider(
      final Spec spec,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final URL externalSignerUrl,
      final BLSPublicKey publicKey,
      final Duration externalSignerTimeout,
      final ThrottlingTaskQueueWithPriority externalSignerTaskQueue,
      final MetricsSystem metricsSystem,
      final boolean readOnly) {
    this.spec = spec;
    this.externalSignerHttpClientFactory = externalSignerHttpClientFactory;
    this.externalSignerUrl = externalSignerUrl;
    this.publicKey = publicKey;
    this.externalSignerTimeout = externalSignerTimeout;
    this.externalSignerTaskQueue = externalSignerTaskQueue;
    this.metricsSystem = metricsSystem;
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
    return new ExternalSigner(
        spec,
        externalSignerHttpClientFactory.get(),
        externalSignerUrl,
        publicKey,
        externalSignerTimeout,
        externalSignerTaskQueue,
        metricsSystem);
  }

  @VisibleForTesting
  public URL getExternalSignerUrl() {
    return externalSignerUrl;
  }
}
