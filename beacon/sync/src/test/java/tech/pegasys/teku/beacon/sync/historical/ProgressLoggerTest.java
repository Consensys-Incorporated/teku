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

package tech.pegasys.teku.beacon.sync.historical;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ProgressLoggerTest {
  private ProgressLogger progressLogger;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final StatusLogger statusLogger = mock(StatusLogger.class);
  private StubTimeProvider timeProvider;

  protected Spec spec = TestSpecFactory.createMinimalPhase0();
  protected DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  public void setup() {
    timeProvider = StubTimeProvider.withTimeInSeconds(0);
    progressLogger = new ProgressLogger(metricsSystem, statusLogger, timeProvider);
  }

  @Test
  public void shouldLogUpdatedStatusAfter5Minutes() {
    timeProvider.advanceTimeBy(Duration.ofMinutes(6));
    final UInt64 currentSlot = UInt64.valueOf(3);
    final UInt64 anchorSlot = UInt64.valueOf(10);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    progressLogger.update(block, anchorSlot);

    verify(statusLogger, times(1)).reconstructedHistoricalBlocks(eq(currentSlot), eq(anchorSlot));
  }

  @Test
  public void shouldLogUpdatedStatusAt5Minutes() {
    timeProvider.advanceTimeBy(Duration.ofMinutes(5));
    final UInt64 currentSlot = UInt64.valueOf(3);
    final UInt64 anchorSlot = UInt64.valueOf(10);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    progressLogger.update(block, anchorSlot);

    verify(statusLogger, times(1)).reconstructedHistoricalBlocks(eq(currentSlot), eq(anchorSlot));
  }

  @Test
  public void shouldNotLogUpdatedStatusBefore5Minutes() {
    final UInt64 anchorSlot = UInt64.valueOf(10);
    timeProvider.advanceTimeBy(Duration.ofMinutes(4));
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));
    progressLogger.update(block, anchorSlot);

    timeProvider.advanceTimeBy(Duration.ofSeconds(59));
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(4));
    progressLogger.update(block2, anchorSlot);

    verify(statusLogger, never()).reconstructedHistoricalBlocks(any(), any());
  }
}
