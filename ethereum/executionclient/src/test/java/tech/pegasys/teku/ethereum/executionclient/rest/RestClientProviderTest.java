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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

class RestClientProviderTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final TimeProvider STUB_TIME_PROVIDER = StubTimeProvider.withTimeInSeconds(10);

  @Test
  void createsStubProvider(@TempDir final Path tempDir) {
    RestClientProvider restClientProvider =
        RestClientProvider.create(
            ExecutionLayerChannel.STUB_ENDPOINT_PREFIX,
            TIMEOUT,
            false,
            Optional.empty(),
            Optional.empty(),
            tempDir,
            STUB_TIME_PROVIDER);

    assertThat(restClientProvider.isStub()).isTrue();

    Assertions.assertThrows(
        InvalidConfigurationException.class, restClientProvider::getRestClient, "Stub provider");
  }

  @Test
  void createsOkHttpRestClientProvider(@TempDir final Path tempDir) {
    RestClientProvider restClientProvider =
        RestClientProvider.create(
            "http://127.0.0.1:28545",
            TIMEOUT,
            false,
            Optional.empty(),
            Optional.empty(),
            tempDir,
            STUB_TIME_PROVIDER);

    assertThat(restClientProvider.isStub()).isFalse();
    assertThat(restClientProvider.getRestClient()).isInstanceOf(OkHttpRestClient.class);
  }
}
