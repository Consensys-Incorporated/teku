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

import java.net.URI;
import java.nio.file.Path;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class ExecutionEngineClientFactory {

  /**
   * Creates an {@link ExecutionEngineClient} based on the endpoint URI scheme.
   *
   * <p>Supported schemes:
   *
   * <ul>
   *   <li>{@code http://}, {@code https://} — HTTP JSON-RPC client
   *   <li>{@code ws://}, {@code wss://} — WebSocket JSON-RPC client
   *   <li>{@code file://} — IPC client over Unix domain socket
   * </ul>
   *
   * <p>The {@code httpClient} and {@code asyncRunnerSupplier} are lazily evaluated and only invoked
   * when the selected transport requires them: {@code httpClient} for HTTP and WebSocket, {@code
   * asyncRunnerSupplier} for IPC.
   *
   * @param endpoint the execution engine endpoint URI (e.g. {@code http://localhost:8551}, {@code
   *     file:///tmp/geth.ipc})
   * @param timeProvider provides the current time for error rate limiting
   * @param eventLog logger for execution client availability events
   * @param executionClientEventsPublisher channel to publish execution client online/offline events
   * @param httpClient supplier for the OkHttpClient instance, used by HTTP and WebSocket transports
   * @param asyncRunnerSupplier supplier for the AsyncRunner instance, used by the IPC transport for
   *     its reader loop
   * @return an {@link ExecutionEngineClient} configured for the given endpoint
   * @throws InvalidConfigurationException if the endpoint scheme is null or unsupported
   */
  public static ExecutionEngineClient create(
      final String endpoint,
      final TimeProvider timeProvider,
      final EventLogger eventLog,
      final ExecutionClientEventsChannel executionClientEventsPublisher,
      final Supplier<OkHttpClient> httpClient,
      final Supplier<AsyncRunner> asyncRunnerSupplier) {
    final URI uri = URI.create(endpoint);
    final String scheme = uri.getScheme();
    if (scheme == null) {
      throw invalidConfigurationException(endpoint);
    }
    return switch (scheme) {
      case "http", "https" ->
          new OkHttpHttpExecutionEngineClient(
              httpClient.get(), endpoint, eventLog, timeProvider, executionClientEventsPublisher);
      case "ws", "wss" ->
          new OkHttpWebSocketExecutionEngineClient(
              httpClient.get(), endpoint, eventLog, timeProvider, executionClientEventsPublisher);
      case "file" ->
          new IpcSocketExecutionEngineClient(
              asyncRunnerSupplier.get(),
              Path.of(uri.getPath()),
              eventLog,
              timeProvider,
              executionClientEventsPublisher);
      default -> throw invalidConfigurationException(endpoint);
    };
  }

  private static InvalidConfigurationException invalidConfigurationException(
      final String endpoint) {
    return new InvalidConfigurationException(
        String.format(
            "Endpoint \"%s\" scheme is not supported. Use http://, https://, ws://, wss://, or file://",
            endpoint));
  }
}
