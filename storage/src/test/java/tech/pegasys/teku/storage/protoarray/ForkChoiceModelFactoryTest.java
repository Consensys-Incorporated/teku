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

package tech.pegasys.teku.storage.protoarray;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class ForkChoiceModelFactoryTest {

  @Test
  void shouldKeepPhase0ModelBeforeGloasFork() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(2);
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
    final ForkChoiceModelFactory factory = new ForkChoiceModelFactory(spec);

    final UInt64 firstGloasSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);

    assertThat(factory.forSlot(firstGloasSlot.minus(1))).isInstanceOf(ForkChoiceModelPhase0.class);
  }

  @Test
  void shouldUseGloasModelAtGloasFork() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(2);
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
    final ForkChoiceModelFactory factory = new ForkChoiceModelFactory(spec);

    final UInt64 firstGloasSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch);

    assertThat(factory.forSlot(firstGloasSlot)).isInstanceOf(ForkChoiceModelGloas.class);
  }
}
