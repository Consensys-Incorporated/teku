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

package tech.pegasys.teku.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class ExecutionClientDataProvider
    implements ExecutionClientEventsChannel, ExecutionClientVersionChannel {

  private final AtomicBoolean isExecutionClientAvailable = new AtomicBoolean(true);
  private volatile Optional<ClientVersion> executionClientVersion = Optional.empty();

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    isExecutionClientAvailable.set(isAvailable);
  }

  @Override
  public void onExecutionClientVersion(final ClientVersion version) {
    executionClientVersion = Optional.of(version);
  }

  @Override
  public void onExecutionClientVersionNotAvailable() {
    executionClientVersion = Optional.empty();
  }

  public Optional<ClientVersion> getExecutionClientVersion() {
    return executionClientVersion;
  }

  public boolean isExecutionClientAvailable() {
    return isExecutionClientAvailable.get();
  }
}
