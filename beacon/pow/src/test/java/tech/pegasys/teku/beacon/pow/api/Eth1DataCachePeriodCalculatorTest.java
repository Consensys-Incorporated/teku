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

package tech.pegasys.teku.beacon.pow.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

class Eth1DataCachePeriodCalculatorTest {
  private final SpecConfig config = SpecConfigLoader.loadConfig("minimal").specConfig();

  @Test
  void shouldCalculateCachePeriodForMinimalConstantsFromFollowDistance() {
    assertThat(
            Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToFollowDistance(
                config))
        .isEqualTo(UInt64.valueOf(470));
  }

  @Test
  void shouldCalculateCachePeriodForMinimalConstantsFromCurrentTime() {
    assertThat(
            Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToCurrentTime(config))
        .isEqualTo(UInt64.valueOf(694));
  }
}
