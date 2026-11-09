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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ExecutionPayloadBidGossipValidatorGasLimitTest {

  @ParameterizedTest
  @MethodSource("provideGasLimitTargetCompatibilityArguments")
  void isGasLimitTargetCompatible_shouldMatchSpecCases(
      final UInt64 parentGasLimit,
      final UInt64 gasLimit,
      final UInt64 targetGasLimit,
      final boolean expectedResult) {
    assertThat(
            ExecutionPayloadBidGossipValidator.isGasLimitTargetCompatible(
                parentGasLimit, gasLimit, targetGasLimit))
        .isEqualTo(expectedResult);
  }

  private static Stream<Arguments> provideGasLimitTargetCompatibilityArguments() {
    return Stream.of(
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(60_000_100),
            UInt64.valueOf(60_000_100),
            true),
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(60_058_592),
            UInt64.valueOf(100_000_000),
            true),
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(60_058_593),
            UInt64.valueOf(100_000_000),
            false),
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(59_999_990),
            UInt64.valueOf(59_999_990),
            true),
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(59_941_408),
            UInt64.valueOf(30_000_000),
            true),
        Arguments.of(
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(60_000_000),
            UInt64.valueOf(60_000_000),
            true),
        Arguments.of(UInt64.valueOf(1023), UInt64.valueOf(1023), UInt64.valueOf(60_000_000), true));
  }
}
