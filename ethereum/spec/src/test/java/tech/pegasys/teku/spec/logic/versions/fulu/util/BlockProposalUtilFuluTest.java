/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.fulu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProposalUtilFuluTest {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Bytes32 previousTargetRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 currentTargetRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();

  public static Stream<Arguments> getStateSlotForProposerDutiesTestCases() {
    // | EPOCH | START SLOT | END SLOT |
    // |   0   |       0    |     7    |
    // |   1   |       8    |    15    |
    // |   2   |      16    |    23    |
    // |   3   |      24    |    31    |
    return Stream.of(
        Arguments.of(1, 1, 8),
        Arguments.of(2, 2, 16),
        Arguments.of(2, 1, 8),
        Arguments.of(3, 1, 16),
        Arguments.of(3, 3, 24),
        Arguments.of(4, 1, 24));
  }

  enum ExpectedResult {
    PREVIOUS,
    CURRENT,
    HEAD,
    FAILURE
  }

  @ParameterizedTest
  @MethodSource("getStateSlotForProposerDutiesTestCases")
  public void getStateSlotForProposerDuties(
      final int requestedEpoch, final int headEpochIn, final int expectedSlot) {
    final UInt64 expectedEpoch = spec.computeEpochAtSlot(UInt64.valueOf(expectedSlot));
    final UInt64 headEpoch = UInt64.valueOf(headEpochIn);
    final UInt64 querySlot =
        spec.getGenesisSpec()
            .getBlockProposalUtil()
            .getStateSlotForProposerDuties(spec, headEpoch, UInt64.valueOf(requestedEpoch));

    LOG.debug(
        "headEpoch={}, dutiesEpoch={}, expectedSlot={} (epoch={})",
        headEpoch,
        requestedEpoch,
        expectedSlot,
        expectedEpoch);
    LOG.debug("resultSlot={}. epoch={}", querySlot, spec.computeEpochAtSlot(querySlot));
    assertThat(querySlot.intValue()).isEqualTo(expectedSlot);
  }

  public static Stream<Arguments> blockProposalDependentRootTestCases() {
    return Stream.of(
        Arguments.of(1, 3, ExpectedResult.HEAD),
        Arguments.of(1, 2, ExpectedResult.CURRENT),
        Arguments.of(1, 1, ExpectedResult.PREVIOUS),
        Arguments.of(1, 0, ExpectedResult.FAILURE));
  }

  @ParameterizedTest
  @MethodSource("blockProposalDependentRootTestCases")
  public void getBlockProposalDependentRoot(
      final int headEpochInt, final int dutiesEpochInt, final ExpectedResult expectedResult) {
    final UInt64 headEpoch = UInt64.valueOf(headEpochInt);
    final UInt64 dutiesEpoch = UInt64.valueOf(dutiesEpochInt);
    LOG.debug(
        "HEAD: {}, CURRENT: {}, PREVIOUS: {}",
        headBlockRoot,
        currentTargetRoot,
        previousTargetRoot);

    if (expectedResult == ExpectedResult.FAILURE) {
      assertThatThrownBy(
              () ->
                  spec.getGenesisSpec()
                      .getBlockProposalUtil()
                      .getBlockProposalDependentRoot(
                          headBlockRoot,
                          previousTargetRoot,
                          currentTargetRoot,
                          headEpoch,
                          dutiesEpoch))
          .hasMessageContaining("Attempting to calculate dependent root");
    } else {
      final Bytes32 expected =
          switch (expectedResult) {
            case PREVIOUS -> previousTargetRoot;
            case CURRENT -> currentTargetRoot;
            case HEAD -> headBlockRoot;
            case FAILURE -> Bytes32.ZERO;
          };
      LOG.debug("Expected: {}", expected);
      assertThat(
              spec.getGenesisSpec()
                  .getBlockProposalUtil()
                  .getBlockProposalDependentRoot(
                      headBlockRoot, previousTargetRoot, currentTargetRoot, headEpoch, dutiesEpoch))
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @MethodSource("blockProposalDependentRootTestCases")
  public void getBlockProposalDependentRoot_fallthrough(
      final int headEpochInt, final int dutiesEpochInt, final ExpectedResult expectedResult) {
    final UInt64 headEpoch = UInt64.valueOf(headEpochInt);
    final UInt64 dutiesEpoch = UInt64.valueOf(dutiesEpochInt);
    final Spec localSpec = TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.valueOf(100));

    LOG.debug(
        "HEAD: {}, CURRENT: {}, PREVIOUS: {}",
        headBlockRoot,
        currentTargetRoot,
        previousTargetRoot);

    final Bytes32 expected = headEpoch.equals(dutiesEpoch) ? currentTargetRoot : headBlockRoot;
    if (expectedResult == ExpectedResult.FAILURE) {
      assertThatThrownBy(
              () ->
                  localSpec
                      .getGenesisSpec()
                      .getBlockProposalUtil()
                      .getBlockProposalDependentRoot(
                          headBlockRoot,
                          previousTargetRoot,
                          currentTargetRoot,
                          headEpoch,
                          dutiesEpoch))
          .hasMessageContaining("Attempting to calculate dependent root");
    } else {
      assertThat(
              localSpec
                  .getGenesisSpec()
                  .getBlockProposalUtil()
                  .getBlockProposalDependentRoot(
                      headBlockRoot, previousTargetRoot, currentTargetRoot, headEpoch, dutiesEpoch))
          .isEqualTo(expected);
    }
  }
}
