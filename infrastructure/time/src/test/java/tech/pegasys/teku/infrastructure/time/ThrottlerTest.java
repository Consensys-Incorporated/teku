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

package tech.pegasys.teku.infrastructure.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ThrottlerTest {
  private final AtomicInteger resource = new AtomicInteger(0);
  private final UInt64 throttlingPeriod = UInt64.valueOf(10);
  private final Throttler<AtomicInteger> throttler = new Throttler<>(resource, throttlingPeriod);

  @Test
  public void init_mustThrowWhenThrottlingPeriodIsNull() {
    assertThatThrownBy(() -> new Throttler<>(resource, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Missing throttling period");
  }

  @Test
  public void invoke_mustThrowWhenCurrentTimeIsNull() {
    assertThatThrownBy(() -> throttler.invoke(null, AtomicInteger::incrementAndGet))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Missing current time");
    assertThat(resource.get()).isEqualTo(0);
  }

  @Test
  public void invoke_initialInvocationShouldRun_atTimeZero() {
    throttler.invoke(UInt64.ZERO, AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);
  }

  @Test
  public void invoke_initialInvocationShouldRun_atTimeGreaterThanZero() {
    throttler.invoke(UInt64.valueOf(99), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);
  }

  @Test
  public void invoke_shouldThrottle() {
    final UInt64 initialTime = UInt64.valueOf(21);
    throttler.invoke(initialTime, AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);

    // Repeatedly invoke at initial time
    for (int i = 0; i < throttlingPeriod.times(2).intValue(); i++) {
      throttler.invoke(initialTime, AtomicInteger::incrementAndGet);
      assertThat(resource.get()).isEqualTo(1);
    }

    // Increment time and invoke up to limit
    for (int i = 0; i < throttlingPeriod.intValue(); i++) {
      throttler.invoke(initialTime.plus(i), AtomicInteger::incrementAndGet);
    }
    assertThat(resource.get()).isEqualTo(1);

    // Invoke at boundary
    throttler.invoke(initialTime.plus(throttlingPeriod), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(2);
  }

  @Test
  public void invoke_shouldNotThrottleAcrossSparseInvocations() {
    final UInt64 initialTime = UInt64.valueOf(21);
    throttler.invoke(initialTime, AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);

    throttler.invoke(initialTime.plus(throttlingPeriod.times(2)), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(2);

    throttler.invoke(
        initialTime.plus(throttlingPeriod.times(3)).plus(1), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(3);
  }

  @Test
  public void invoke_shouldThrottleAllInvocationsFromThePast() {
    final UInt64 initialTime = UInt64.valueOf(2000);
    throttler.invoke(initialTime, AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);

    for (UInt64 i = UInt64.ZERO; i.isLessThan(initialTime); i = i.plus(22)) {
      throttler.invoke(i, AtomicInteger::incrementAndGet);
      assertThat(resource.get()).isEqualTo(1);
    }
  }

  @Test
  public void invoke_shouldThrottleBasedOnLastSuccessfulInvocation() {
    UInt64 lastInvocation = UInt64.valueOf(21);
    throttler.invoke(UInt64.valueOf(21), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);

    // Don't throttle under the next threshold
    throttler.invoke(
        lastInvocation.plus(throttlingPeriod).minus(1), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(1);

    // Invoke once we pass the current threshold
    lastInvocation = lastInvocation.plus(throttlingPeriod.times(2)).plus(1);
    throttler.invoke(lastInvocation, AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(2);

    // Don't throttle under the next threshold
    throttler.invoke(
        lastInvocation.plus(throttlingPeriod).minus(1), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(2);
    // Invoke at next threshold
    throttler.invoke(lastInvocation.plus(throttlingPeriod), AtomicInteger::incrementAndGet);
    assertThat(resource.get()).isEqualTo(3);
  }
}
