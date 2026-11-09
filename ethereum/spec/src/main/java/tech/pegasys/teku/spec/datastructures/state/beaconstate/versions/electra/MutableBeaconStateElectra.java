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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public interface MutableBeaconStateElectra extends MutableBeaconStateDeneb, BeaconStateElectra {
  static MutableBeaconStateElectra required(final MutableBeaconState state) {
    return state
        .toMutableVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Electra state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateElectra commitChanges();

  @Override
  default Optional<MutableBeaconStateElectra> toMutableVersionElectra() {
    return Optional.of(this);
  }

  default void setDepositRequestsStartIndex(final UInt64 depositRequestsStartIndex) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX);
    set(fieldIndex, SszUInt64.of(depositRequestsStartIndex));
  }

  default void setDepositBalanceToConsume(final UInt64 depositBalanceToConsume) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME);
    set(fieldIndex, SszUInt64.of(depositBalanceToConsume));
  }

  default void setExitBalanceToConsume(final UInt64 exitBalanceToConsume) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.EXIT_BALANCE_TO_CONSUME);
    set(fieldIndex, SszUInt64.of(exitBalanceToConsume));
  }

  default void setEarliestExitEpoch(final UInt64 earliestExitEpoch) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.EARLIEST_EXIT_EPOCH);
    set(fieldIndex, SszUInt64.of(earliestExitEpoch));
  }

  default void setConsolidationBalanceToConsume(final UInt64 consolidationBalanceToConsume) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CONSOLIDATION_BALANCE_TO_CONSUME);
    set(fieldIndex, SszUInt64.of(consolidationBalanceToConsume));
  }

  default void setEarliestConsolidationEpoch(final UInt64 earliestConsolidationEpoch) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.EARLIEST_CONSOLIDATION_EPOCH);
    set(fieldIndex, SszUInt64.of(earliestConsolidationEpoch));
  }

  default void setPendingDeposits(final SszList<PendingDeposit> pendingDeposits) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PENDING_DEPOSITS);
    set(fieldIndex, pendingDeposits);
  }

  @Override
  default SszMutableList<PendingDeposit> getPendingDeposits() {
    final int index = getSchema().getFieldIndex(PENDING_DEPOSITS);
    return getAnyByRef(index);
  }

  default void setPendingPartialWithdrawals(
      final SszList<PendingPartialWithdrawal> pendingPartialWithdrawals) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS);
    set(fieldIndex, pendingPartialWithdrawals);
  }

  @Override
  default SszMutableList<PendingPartialWithdrawal> getPendingPartialWithdrawals() {
    final int index = getSchema().getFieldIndex(PENDING_PARTIAL_WITHDRAWALS);
    return getAnyByRef(index);
  }

  default void setPendingConsolidations(final SszList<PendingConsolidation> pendingConsolidations) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PENDING_CONSOLIDATIONS);
    set(fieldIndex, pendingConsolidations);
  }

  @Override
  default SszMutableList<PendingConsolidation> getPendingConsolidations() {
    final int index = getSchema().getFieldIndex(PENDING_CONSOLIDATIONS);
    return getAnyByRef(index);
  }
}
