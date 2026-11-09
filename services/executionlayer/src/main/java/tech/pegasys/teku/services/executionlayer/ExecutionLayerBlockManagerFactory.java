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

package tech.pegasys.teku.services.executionlayer;

import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerBlockProductionManagerImpl;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerBlockManagerFactory {
  public static ExecutionLayerBlockProductionManager create(
      final ExecutionLayerChannel executionLayerManager, final EventChannels eventChannels) {
    final ExecutionLayerBlockProductionManagerImpl executionLayerBlockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
    eventChannels.subscribe(SlotEventsChannel.class, executionLayerBlockProductionManager);
    return executionLayerBlockProductionManager;
  }

  public static ExecutionLayerBlockProductionManager createDummy() {
    return ExecutionLayerBlockProductionManager.NOOP;
  }
}
