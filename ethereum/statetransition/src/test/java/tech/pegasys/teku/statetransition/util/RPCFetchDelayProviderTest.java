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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MIN_WAIT_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_TARGET_WAIT_MILLIS;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.statetransition.datacolumns.CurrentSlotProvider;
import tech.pegasys.teku.storage.client.RecentChainData;

public class RPCFetchDelayProviderTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final CurrentSlotProvider currentSlotProvider = mock(CurrentSlotProvider.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private final UInt64 currentSlot = UInt64.valueOf(10);

  private final RPCFetchDelayProvider rpcFetchDelayProvider =
      RPCFetchDelayProvider.create(
          spec,
          timeProvider,
          recentChainData,
          currentSlotProvider,
          DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS,
          DEFAULT_MIN_WAIT_MILLIS,
          DEFAULT_TARGET_WAIT_MILLIS);

  @BeforeEach
  public void setUp() {
    when(currentSlotProvider.getCurrentSlot()).thenReturn(currentSlot);
  }

  @Test
  void shouldRespectTargetWhenBlockIsEarly() {
    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // first block\dataSidecar arrives at slot start
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.longValue());

    final Duration fetchDelay = rpcFetchDelayProvider.calculate(currentSlot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(DEFAULT_TARGET_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateBlockFetchDelay_shouldRespectMinimumWhenRpcIsLate() {
    final UInt64 startSlotInSeconds = UInt64.valueOf(10);
    final UInt64 startSlotInMillis = startSlotInSeconds.times(1_000);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // first block\dataSidecar arrives 200ms before attestation due
    timeProvider.advanceTimeByMillis(startSlotInMillis.plus(3_800).longValue());

    final Duration fetchDelay = rpcFetchDelayProvider.calculate(currentSlot);

    // we can't wait less than min
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(DEFAULT_MIN_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateBlockFetchDelay_shouldRespectTargetWhenRpcIsVeryLate() {
    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // first block\dataSidecar arrives 1s after attestation due
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.plus(5).longValue());

    final Duration fetchDelay = rpcFetchDelayProvider.calculate(currentSlot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(DEFAULT_TARGET_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateRpcFetchDelay_shouldRespectAttestationDueLimit() {
    final UInt64 startSlotInSeconds = UInt64.valueOf(10);
    final UInt64 startSlotInMillis = startSlotInSeconds.times(1_000);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    final UInt64 millisecondsIntoAttDueLimit = UInt64.valueOf(200);

    // first block\dataSidecar arrival is 200ms over the max wait relative to the attestation due
    final UInt64 blockArrivalTimeMillis =
        startSlotInMillis
            .plus(4_000)
            .minus(DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS)
            .plus(millisecondsIntoAttDueLimit)
            .minus(DEFAULT_TARGET_WAIT_MILLIS);

    timeProvider.advanceTimeByMillis(blockArrivalTimeMillis.longValue());

    final Duration fetchDelay = rpcFetchDelayProvider.calculate(currentSlot);

    // we can only wait 200ms less than target
    // note the extra 1ms is from the difference of 1/3 slot time vs the 3333 basis points
    assertThat(fetchDelay)
        .isEqualTo(
            Duration.ofMillis(
                DEFAULT_TARGET_WAIT_MILLIS
                    .minus(millisecondsIntoAttDueLimit)
                    .minus(1)
                    .longValue()));
  }

  @Test
  void calculateRpcFetchDelay_shouldReturnZeroIfSlotIsOld() {
    final Duration fetchDelay = rpcFetchDelayProvider.calculate(currentSlot.decrement());

    assertThat(fetchDelay).isEqualTo(Duration.ZERO);
  }
}
