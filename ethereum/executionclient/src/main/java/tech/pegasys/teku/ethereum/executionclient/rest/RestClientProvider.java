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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.PREVIOUS_STUB_ENDPOINT_PREFIX;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public interface RestClientProvider {

  RestClientProvider STUB =
      new RestClientProvider() {

        @Override
        public RestClient getRestClient() {
          throw new InvalidConfigurationException("Stub provider");
        }

        @Override
        public boolean isStub() {
          return true;
        }
      };

  static RestClientProvider create(
      final String endpoint,
      final Duration timeout,
      final boolean jwtSupported,
      final Optional<String> jwtSecretFile,
      final Optional<String> jwtClaimId,
      final Path beaconDataDirectory,
      final TimeProvider timeProvider) {
    if (endpoint.startsWith(PREVIOUS_STUB_ENDPOINT_PREFIX)) {
      throw new InvalidConfigurationException(
          "Using the stub execution engine is unsafe. This is only designed for testing. Please use a real execution client.");
    } else if (endpoint.startsWith(STUB_ENDPOINT_PREFIX)) {
      return STUB;
    } else {
      final Optional<JwtConfig> jwtConfig =
          JwtConfig.createIfNeeded(jwtSupported, jwtSecretFile, jwtClaimId, beaconDataDirectory);
      return new OkHttpRestClientProvider(endpoint, timeout, jwtConfig, timeProvider);
    }
  }

  RestClient getRestClient();

  boolean isStub();
}
