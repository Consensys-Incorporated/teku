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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ValidatorStatusTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final ValidatorStatusFactory validatorStatusFactory =
      genesisSpec.getValidatorStatusFactory();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldSetFlagToTrueWheNewValueTrue(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsFalseWheNewValueFalse(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, false);
    assertThat(getter.apply(status)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsTrueWheNewValueFalse(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
    setter.accept(status, false);
    assertThat(getter.apply(status)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsTrueWheNewValueTrue(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
  }

  @Test
  void shouldUpdateInclusionInfoWhenNoPreviousValueSet() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(newValue);
  }

  @Test
  void shouldLeaveInclusionInfoEmptyWhenNewValueIsEmpty() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    status.updateInclusionInfo(Optional.empty());
    assertThat(status.getInclusionInfo()).isEmpty();
  }

  @Test
  void shouldNotChangeInclusionInfoWhenNewValueIsEmpty() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.empty());
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  @Test
  void shouldUseNewInclusionInfoWhenItHasLowerDelay() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ZERO, UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(newValue);
  }

  @Test
  void shouldKeepOldInclusionInfoWhenNewValueHasEqualDelay() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ONE, UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  @Test
  void shouldKeepOldInclusionInfoWhenNewValueHasHigherDelay() {
    final ValidatorStatus status =
        validatorStatusFactory.createValidatorStatus(
            dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.valueOf(2), UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  static Stream<Arguments> flags() {
    return Stream.of(
        flag(
            "currentEpochSourceAttester",
            ValidatorStatus::isCurrentEpochSourceAttester,
            ValidatorStatus::updateCurrentEpochSourceAttester),
        flag(
            "currentEpochTargetAttester",
            ValidatorStatus::isCurrentEpochTargetAttester,
            ValidatorStatus::updateCurrentEpochTargetAttester),
        flag(
            "previousEpochSourceAttester",
            ValidatorStatus::isPreviousEpochSourceAttester,
            ValidatorStatus::updatePreviousEpochSourceAttester),
        flag(
            "previousEpochTargetAttester",
            ValidatorStatus::isPreviousEpochTargetAttester,
            ValidatorStatus::updatePreviousEpochTargetAttester),
        flag(
            "previousEpochHeadAttester",
            ValidatorStatus::isPreviousEpochHeadAttester,
            ValidatorStatus::updatePreviousEpochHeadAttester));
  }

  private static Arguments flag(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    return Arguments.of(name, getter, setter);
  }
}
