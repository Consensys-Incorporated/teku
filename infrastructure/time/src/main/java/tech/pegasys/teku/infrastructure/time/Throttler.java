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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Throttler<TResource> {
  // The wrapped resource can be invoked at most once every throttling period
  private final TResource resource;
  private final UInt64 throttlingPeriod;
  private final AtomicReference<UInt64> lastInvoked = new AtomicReference<>(null);

  public Throttler(final TResource resource, final UInt64 throttlingPeriod) {
    checkNotNull(throttlingPeriod, "Missing throttling period");
    this.resource = resource;
    this.throttlingPeriod = throttlingPeriod;
  }

  public void invoke(final UInt64 currentTime, final Consumer<TResource> invocation) {
    checkNotNull(currentTime, "Missing current time");
    if (updateLastInvoked(currentTime)) {
      invocation.accept(resource);
    }
  }

  /**
   * If the event at the current time should be throttled, does not update lastInvoked and returns
   * false. Otherwise, updates lastInvoked and returns true.
   *
   * @param currentTime The current time
   * @return True if lastInvoked was updated (event should not be throttled), false otherwise
   */
  private boolean updateLastInvoked(final UInt64 currentTime) {
    final UInt64 previousValue =
        lastInvoked.getAndUpdate(
            last -> shouldThrottleEventAtTime(last, currentTime) ? last : currentTime);
    return !shouldThrottleEventAtTime(previousValue, currentTime);
  }

  private boolean shouldThrottleEventAtTime(final UInt64 lastInvoked, final UInt64 currentTime) {
    return lastInvoked != null && lastInvoked.plus(throttlingPeriod).isGreaterThan(currentTime);
  }
}
