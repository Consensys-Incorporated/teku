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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class RetryDelayFunctionTest {

  @Test
  public void createExponentialRetry() {
    RetryDelayFunction retryFn =
        RetryDelayFunction.createExponentialRetry(2, Duration.ofSeconds(5), Duration.ofMinutes(1));

    assertRetryDelay(retryFn, 0, Duration.ofSeconds(5));
    assertRetryDelay(retryFn, 1, Duration.ofSeconds(10));
    assertRetryDelay(retryFn, 2, Duration.ofSeconds(20));
    assertRetryDelay(retryFn, 3, Duration.ofSeconds(40));
    assertRetryDelay(retryFn, 4, Duration.ofMinutes(1));
    assertRetryDelay(retryFn, 5, Duration.ofMinutes(1));

    // Check again to make sure results are not dependent on internal state
    assertRetryDelay(retryFn, 0, Duration.ofSeconds(5));
    assertRetryDelay(retryFn, 1, Duration.ofSeconds(10));
    assertRetryDelay(retryFn, 2, Duration.ofSeconds(20));
    assertRetryDelay(retryFn, 3, Duration.ofSeconds(40));
    assertRetryDelay(retryFn, 4, Duration.ofMinutes(1));
    assertRetryDelay(retryFn, 5, Duration.ofMinutes(1));
  }

  private void assertRetryDelay(
      final RetryDelayFunction retryFn, final int retries, final Duration expectedResult) {
    assertThat(retryFn.getRetryDelay(retries).toMillis()).isEqualTo(expectedResult.toMillis());
  }
}
