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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;

class ValidatorSourceFactoryTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final InteropConfig disabledInteropConfig =
      InteropConfig.builder().specProvider(spec).build();
  final ValidatorConfig config =
      ValidatorConfig.builder().validatorExternalSignerSlashingProtectionEnabled(false).build();

  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final PublicKeyLoader publicKeyLoader = new PublicKeyLoader();

  @Test
  void mutableValidatorShouldBeSlashingProtected(@TempDir final Path tempDir) {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(
            tempDir, Optional.empty(), Optional.empty(), Optional.empty(), false);
    final ValidatorSourceFactory factory =
        new ValidatorSourceFactory(
            spec,
            config,
            disabledInteropConfig,
            () -> httpClient,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.of(dataDirLayout));
    factory.createValidatorSources();
    final Optional<ValidatorSource> source = factory.getMutableLocalValidatorSource();
    assertThat(source).isPresent();
    assertThat(source.get()).isInstanceOf(SlashingProtectedValidatorSource.class);
  }
}
