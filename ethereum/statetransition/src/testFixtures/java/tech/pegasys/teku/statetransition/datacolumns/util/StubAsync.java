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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static java.time.Duration.ofMillis;

import java.time.Duration;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class StubAsync {
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  public AsyncRunner getStubAsyncRunner() {
    return stubAsyncRunner;
  }

  public void advanceTimeGradually(final Duration delta) {
    for (long i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  public void advanceTimeGraduallyUntilAllDone(final Duration maxAdvancePeriod) {
    for (long i = 0; i < maxAdvancePeriod.toMillis(); i++) {
      if (!stubAsyncRunner.hasDelayedActions()) {
        return;
      }
      stubTimeProvider.advanceTimeBy(ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
    throw new AssertionError(
        "There are still scheduled tasks after maxAdvancePeriod: " + maxAdvancePeriod);
  }
}
