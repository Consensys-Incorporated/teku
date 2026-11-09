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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerServiceTest {
  private static final Logger LOG = LogManager.getLogger();
  private final EventChannels eventChannels = mock(EventChannels.class);
  private final ExecutionLayerManager executionLayerManager = mock(ExecutionLayerManager.class);

  private final ExecutionLayerService underTest =
      new ExecutionLayerService(eventChannels, executionLayerManager);

  @Test
  public void addsSubscribersToTheEventChannelOnServiceStart() {
    when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);

    underTest.start().finishDebug(LOG);

    verify(eventChannels).subscribe(SlotEventsChannel.class, executionLayerManager);
    verify(eventChannels).subscribe(ExecutionLayerChannel.class, executionLayerManager);

    underTest.stop().finishDebug(LOG);
  }
}
