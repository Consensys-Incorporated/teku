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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StubTimeProvider implements TimeProvider {

  private UInt64 timeInMillis;

  private StubTimeProvider(final UInt64 timeInMillis) {
    this.timeInMillis = timeInMillis;
  }

  public static StubTimeProvider withTimeInSeconds(final long timeInSeconds) {
    return withTimeInSeconds(UInt64.valueOf(timeInSeconds));
  }

  public static StubTimeProvider withTimeInSeconds(final UInt64 timeInSeconds) {
    return withTimeInMillis(timeInSeconds.times(MILLIS_PER_SECOND));
  }

  public static StubTimeProvider withTimeInMillis(final long timeInMillis) {
    return withTimeInMillis(UInt64.valueOf(timeInMillis));
  }

  public static StubTimeProvider withTimeInMillis(final UInt64 timeInMillis) {
    return new StubTimeProvider(timeInMillis);
  }

  public void advanceTimeBy(final Duration duration) {
    advanceTimeByMillis(duration.toMillis());
  }

  public void advanceTimeBySeconds(final long seconds) {
    advanceTimeByMillis(TimeUnit.SECONDS.toMillis(seconds));
  }

  public void advanceTimeByMillis(final long millis) {
    this.timeInMillis = timeInMillis.plus(millis);
  }

  public void advanceTimeByMillis(final UInt64 millis) {
    this.timeInMillis = timeInMillis.plus(millis);
  }

  @Override
  public UInt64 getTimeInMillis() {
    return timeInMillis;
  }
}
