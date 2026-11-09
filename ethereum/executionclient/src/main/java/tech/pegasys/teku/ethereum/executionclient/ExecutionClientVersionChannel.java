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

import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public interface ExecutionClientVersionChannel extends VoidReturningChannelInterface {

  /**
   * Provides an execution {@link ClientVersion} based on <a
   * href="https://github.com/ethereum/execution-apis/blob/main/src/engine/identification.md#engine_getclientversionv1">engine_getClientVersion</a>.
   * This method will be called on startup and every time the {@link ClientVersion} changes.
   */
  void onExecutionClientVersion(ClientVersion executionClientVersion);

  /** Called when engine_getClientVersion method is not available or has failed on startup */
  void onExecutionClientVersionNotAvailable();
}
