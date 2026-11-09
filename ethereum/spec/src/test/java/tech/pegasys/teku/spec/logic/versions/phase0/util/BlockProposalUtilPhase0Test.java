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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

class BlockProposalUtilPhase0Test {
  public static Stream<Arguments> getStateSlotForProposerDutiesTestCases() {
    // | EPOCH | START SLOT | END SLOT |
    // |   0   |       0    |     7    |
    // |   1   |       8    |    15    |
    // |   2   |      16    |    23    |
    // |   3   |      24    |    31    |
    return Stream.of(
        Arguments.of(0, 0), Arguments.of(1, 8), Arguments.of(2, 16), Arguments.of(3, 24));
  }

  @ParameterizedTest
  @MethodSource("getStateSlotForProposerDutiesTestCases")
  public void getStateSlotForProposerDuties(final int requestedEpoch, final int expectedSlot) {
    final UInt64 epoch = UInt64.valueOf(requestedEpoch);
    final Spec localSpec = TestSpecFactory.createMinimal(SpecMilestone.PHASE0);
    final UInt64 querySlot =
        localSpec
            .getGenesisSpec()
            .getBlockProposalUtil()
            .getStateSlotForProposerDuties(localSpec, epoch, epoch);

    assertThat(querySlot.intValue()).isEqualTo(expectedSlot);
  }
}
