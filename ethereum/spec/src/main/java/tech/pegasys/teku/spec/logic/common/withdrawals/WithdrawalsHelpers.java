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

package tech.pegasys.teku.spec.logic.common.withdrawals;

import java.util.List;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public interface WithdrawalsHelpers {

  // get_expected_withdrawals
  ExpectedWithdrawals getExpectedWithdrawals(BeaconState state);

  // process_withdrawals
  void processWithdrawals(MutableBeaconState state, ExecutionPayloadSummary payload)
      throws BlockProcessingException;

  // same as above but skipping payload withdrawals root comparison
  void processWithdrawals(MutableBeaconState state);

  record ExpectedWithdrawals(
      List<Withdrawal> withdrawals,
      int processedBuilderWithdrawalsCount,
      int processedPartialWithdrawalsCount,
      int processedBuildersSweepCount,
      int processedValidatorsSweepCount) {}

  static UInt64 getTotalWithdrawn(final List<Withdrawal> withdrawals, final UInt64 validatorIndex) {
    return withdrawals.stream()
        .filter(withdrawal -> withdrawal.getValidatorIndex().equals(validatorIndex))
        .map(Withdrawal::getAmount)
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  static Bytes20 getEthAddressFromWithdrawalCredentials(final Validator validator) {
    return new Bytes20(validator.getWithdrawalCredentials().slice(12));
  }
}
