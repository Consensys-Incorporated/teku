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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PredicatesElectraTest {
  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final PredicatesElectra predicates = new PredicatesElectra(spec.getGenesisSpecConfig());

  private final UInt64 excessLargeValidatorBalance = UInt64.valueOf(2050_000_000_000L);

  private final UInt64 maxEffectiveBalanceNonCompounding = UInt64.THIRTY_TWO_ETH;

  private final UInt64 maxEffectiveBalanceCompounding = UInt64.valueOf(2048_000_000_000L);

  @Test
  void isPartiallyWithdrawableValidator_shouldNotDetermineBlsWithdrawalAsNotWithdrawable() {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(dataStructureUtil.randomBlsWithdrawalCredentials())
            .effectiveBalance(maxEffectiveBalanceNonCompounding)
            .build();
    assertThat(predicates.isPartiallyWithdrawableValidator(validator, excessLargeValidatorBalance))
        .isFalse();
  }

  @Test
  void isPartiallyWithdrawableValidator_shouldDetermineEth1WithdrawalAsWithdrawable() {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(dataStructureUtil.randomEth1WithdrawalCredentials())
            .effectiveBalance(maxEffectiveBalanceNonCompounding)
            .build();
    assertThat(predicates.isPartiallyWithdrawableValidator(validator, excessLargeValidatorBalance))
        .isTrue();
  }

  @Test
  void isPartiallyWithdrawableValidator_shouldDetermineCompoundingWithdrawalAsWithdrawable() {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(dataStructureUtil.randomCompoundingWithdrawalCredentials())
            .effectiveBalance(maxEffectiveBalanceCompounding)
            .build();
    assertThat(predicates.isPartiallyWithdrawableValidator(validator, excessLargeValidatorBalance))
        .isTrue();
  }

  @Test
  void isPartiallyWithdrawableValidator_shouldDetermineCompoundingWithdrawalAsAsNotWithdrawable() {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(dataStructureUtil.randomCompoundingWithdrawalCredentials())
            .effectiveBalance(maxEffectiveBalanceNonCompounding)
            .build();
    assertThat(
            predicates.isPartiallyWithdrawableValidator(
                validator, maxEffectiveBalanceNonCompounding))
        .isFalse();
  }
}
