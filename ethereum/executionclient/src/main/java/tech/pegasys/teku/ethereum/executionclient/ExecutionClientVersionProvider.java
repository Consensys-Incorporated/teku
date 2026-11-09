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

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

/**
 * Based on <a href="https://github.com/ethereum/execution-apis/pull/517">Specify Client Versions on
 * Engine API</a>. This provider will publish EL client version on {@link
 * ExecutionClientVersionChannel} when the EL supports version specification and {@link
 * ExecutionLayerChannel#engineGetClientVersion(ClientVersion)} has been called.
 */
public class ExecutionClientVersionProvider implements ExecutionClientEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final AtomicBoolean lastExecutionClientAvailability = new AtomicBoolean(true);

  private final AtomicReference<ClientVersion> executionClientVersion = new AtomicReference<>();

  private final ExecutionLayerChannel executionLayerChannel;
  private final ExecutionClientVersionChannel executionClientVersionChannel;
  private final ClientVersion consensusClientVersion;

  public ExecutionClientVersionProvider(
      final ExecutionLayerChannel executionLayerChannel,
      final ExecutionClientVersionChannel executionClientVersionChannel,
      final ClientVersion consensusClientVersion) {
    this.executionLayerChannel = executionLayerChannel;
    this.executionClientVersionChannel = executionClientVersionChannel;
    this.consensusClientVersion = consensusClientVersion;
    // update client info on initialization
    updateClientInfo(true);
  }

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    // only update info after EL has been unavailable
    if (isAvailable && lastExecutionClientAvailability.compareAndSet(false, true)) {
      updateClientInfo(false);
    } else {
      lastExecutionClientAvailability.set(isAvailable);
    }
  }

  private void updateClientInfo(final boolean notifyNotAvailable) {
    executionLayerChannel
        .engineGetClientVersion(consensusClientVersion)
        .thenAccept(
            clientVersions -> {
              final ClientVersion executionClientVersion = clientVersions.get(0);
              updateVersionIfNeeded(executionClientVersion);
            })
        .finish(
            ex -> {
              if (notifyNotAvailable) {
                executionClientVersionChannel.onExecutionClientVersionNotAvailable();
              }
              LOG.debug("Exception while calling engine_getClientVersion", ex);
            });
  }

  private synchronized void updateVersionIfNeeded(final ClientVersion executionClientVersion) {
    if (Objects.equals(this.executionClientVersion.get(), executionClientVersion)) {
      return;
    }
    EVENT_LOG.logExecutionClientVersion(
        executionClientVersion.name(), executionClientVersion.version());
    this.executionClientVersion.set(executionClientVersion);
    executionClientVersionChannel.onExecutionClientVersion(executionClientVersion);
  }
}
