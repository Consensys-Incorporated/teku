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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class ExecutionEngineClientFactoryTest {

  private final OkHttpClient httpClient = new OkHttpClient.Builder().build();
  private final AsyncRunner asyncRunner = mock(AsyncRunner.class);
  private final EventLogger eventLog = mock(EventLogger.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:8551", "https://localhost:8551"})
  void createsHttpClient_forHttpEndpoints(final String endpoint) {
    final ExecutionEngineClient client = createClient(endpoint);
    assertThat(client).isInstanceOf(OkHttpHttpExecutionEngineClient.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ws://localhost:8551", "wss://localhost:8551"})
  void createsWebSocketClient_forWsEndpoints(final String endpoint) {
    final ExecutionEngineClient client = createClient(endpoint);
    assertThat(client).isInstanceOf(OkHttpWebSocketExecutionEngineClient.class);
  }

  @Test
  void createsIpcClient_forFileEndpoint() {
    final ExecutionEngineClient client = createClient("file:///tmp/foo.ipc");
    assertThat(client).isInstanceOf(IpcSocketExecutionEngineClient.class);
  }

  @Test
  void throwsException_forUnsupportedScheme() {
    assertThatThrownBy(() -> createClient("ftp://localhost:8551"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("ftp://localhost:8551")
        .hasMessageContaining("not supported");
  }

  @Test
  void throwsException_forMissingScheme() {
    assertThatThrownBy(() -> createClient("localhost:8551"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("not supported");
  }

  private ExecutionEngineClient createClient(final String endpoint) {
    return ExecutionEngineClientFactory.create(
        endpoint,
        timeProvider,
        eventLog,
        executionClientEventsPublisher,
        () -> httpClient,
        () -> asyncRunner);
  }
}
