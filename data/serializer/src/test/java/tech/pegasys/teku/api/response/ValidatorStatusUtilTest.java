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

package tech.pegasys.teku.api.response;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ValidatorStatusUtilTest {
  private static final UInt64 ONE_HUNDRED = UInt64.valueOf(100);
  private static final UInt64 TWO_HUNDRED = UInt64.valueOf(200);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Bytes48 key = dataStructureUtil.randomPublicKeyBytes();
  private final Bytes32 creds = dataStructureUtil.randomBytes32();

  @Test
  void status_shouldBePendingInitialised() {
    final Validator validator = pendingValidator(TWO_HUNDRED, FAR_FUTURE_EPOCH);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.pending_initialized);
  }

  @Test
  void status_shouldBePendingQueued() {
    final Validator validator = pendingValidator(TWO_HUNDRED, TWO_HUNDRED);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.pending_queued);
  }

  @Test
  void status_shouldBeActiveOngoing() {
    final Validator validator = activeValidator(FAR_FUTURE_EPOCH, false);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ZERO, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.active_ongoing);
  }

  @Test
  void status_shouldBeActiveExiting() {
    final Validator validator = activeValidator(TWO_HUNDRED, false);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.active_exiting);
  }

  @Test
  void status_shouldBeActiveSlashed() {
    final Validator validator = activeValidator(TWO_HUNDRED, true);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.active_slashed);
  }

  @Test
  void status_shouldBeExitedUnslashed() {
    final Validator validator = exitedValidator(ONE_HUNDRED, TWO_HUNDRED, false);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.exited_unslashed);
    assertThat(
            ValidatorStatusUtil.getValidatorStatus(
                ONE_HUNDRED.plus(ONE), validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.exited_unslashed);
  }

  @Test
  void status_shouldBeExitedSlashed() {
    final Validator validator = exitedValidator(ONE_HUNDRED, TWO_HUNDRED, true);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.exited_slashed);
  }

  @Test
  void status_shouldBeWithdrawalPossible() {
    final Validator validator = withdrawalValidator(UInt64.valueOf("32000000000"), ONE_HUNDRED);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.withdrawal_possible);
  }

  @Test
  void status_shouldBeWithdrawalDone() {
    final Validator validator = withdrawalValidator(ZERO, ONE_HUNDRED);
    assertThat(ValidatorStatusUtil.getValidatorStatus(ONE_HUNDRED, validator, FAR_FUTURE_EPOCH))
        .isEqualTo(ValidatorStatus.withdrawal_done);
  }

  private Validator withdrawalValidator(final UInt64 balance, final UInt64 withdrawableEpoch) {
    return createValidator(balance, false, ZERO, ZERO, ZERO, withdrawableEpoch);
  }

  private Validator exitedValidator(
      final UInt64 exitEpoch, final UInt64 withdrawableEpoch, final boolean slashed) {
    return createValidator(
        UInt64.valueOf("32000000000"), slashed, ZERO, ZERO, exitEpoch, withdrawableEpoch);
  }

  private Validator activeValidator(final UInt64 exitEpoch, final boolean slashed) {
    return createValidator(
        UInt64.valueOf("32000000000"), slashed, ZERO, ZERO, exitEpoch, FAR_FUTURE_EPOCH);
  }

  private Validator pendingValidator(
      final UInt64 activationEpoch, final UInt64 activationEligibilityEpoch) {
    return createValidator(
        UInt64.valueOf("32000000000"),
        false,
        activationEligibilityEpoch,
        activationEpoch,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
  }

  private Validator createValidator(
      final UInt64 balance,
      final boolean slashed,
      final UInt64 activationEligibilityEpoch,
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 withdrawableEpoch) {
    return new Validator(
        key,
        creds,
        balance,
        slashed,
        activationEligibilityEpoch,
        activationEpoch,
        exitEpoch,
        withdrawableEpoch);
  }
}
