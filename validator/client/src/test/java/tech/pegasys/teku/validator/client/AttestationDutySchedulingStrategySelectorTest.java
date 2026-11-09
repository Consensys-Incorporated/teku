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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

public class AttestationDutySchedulingStrategySelectorTest {

  private static final int MIN_SIZE_TO_BATCH_BY_SLOT = 42;

  private final AttestationDutyDefaultSchedulingStrategy defaultStrategy =
      mock(AttestationDutyDefaultSchedulingStrategy.class);

  private final AttestationDutyBatchSchedulingStrategy batchStrategy =
      mock(AttestationDutyBatchSchedulingStrategy.class);

  private final AttestationDutySchedulingStrategySelector strategySelector =
      new AttestationDutySchedulingStrategySelector(
          MIN_SIZE_TO_BATCH_BY_SLOT, false, defaultStrategy, batchStrategy);

  @Test
  public void selectsDefaultStrategyWhenSizeIsLowerThanMinSizeToBatch() {
    final AttestationDutySchedulingStrategy result = strategySelector.selectStrategy(40);

    assertThat(result).isEqualTo(defaultStrategy);
  }

  @Test
  public void selectsBatchStrategyWhenSizeIsHigherThanMinSizeToBatch() {
    final AttestationDutySchedulingStrategy result = strategySelector.selectStrategy(43);

    assertThat(result).isEqualTo(batchStrategy);
  }

  @Test
  public void selectsDefaultStrategyWhenDvtIsEnabled() {
    final AttestationDutySchedulingStrategySelector strategySelector =
        new AttestationDutySchedulingStrategySelector(
            MIN_SIZE_TO_BATCH_BY_SLOT, true, defaultStrategy, batchStrategy);

    final AttestationDutySchedulingStrategy result = strategySelector.selectStrategy(43);

    assertThat(result).isEqualTo(defaultStrategy);
  }
}
