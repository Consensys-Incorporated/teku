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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Map;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLivenessExpectation {
  private final int start;
  private final int count;
  private final boolean isLive;

  private ValidatorLivenessExpectation(final int start, final int count, final boolean isLive) {
    this.start = start;
    this.count = count;
    this.isLive = isLive;
  }

  public void verify(final Map<UInt64, Boolean> liveness) {
    StringBuilder result = new StringBuilder();
    for (int i = start; i < start + count; i++) {
      if (liveness.get(UInt64.valueOf(i)) != isLive) {
        result.append(
            String.format(
                "Validator %s was expected to be %s, but was not.\n",
                i, isLive ? "live" : "offline"));
      }
    }
    assertThat(result.toString()).isEmpty();
  }

  public static ValidatorLivenessExpectation expectLive(final int start, final int count) {
    return new ValidatorLivenessExpectation(start, count, true);
  }

  public static ValidatorLivenessExpectation expectNotLive(final int start, final int count) {
    return new ValidatorLivenessExpectation(start, count, false);
  }
}
