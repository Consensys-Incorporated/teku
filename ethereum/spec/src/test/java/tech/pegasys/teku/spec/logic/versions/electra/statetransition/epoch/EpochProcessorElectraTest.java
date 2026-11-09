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

package tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class EpochProcessorElectraTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final EpochProcessorElectra epochProcessor =
      (EpochProcessorElectra) spec.getGenesisSpec().getEpochProcessor();

  @Test
  public void shouldCheckNewValidatorsDuringEpochProcessingReturnsTrue() {
    assertThat(epochProcessor.shouldCheckNewValidatorsDuringEpochProcessing()).isTrue();
  }
}
