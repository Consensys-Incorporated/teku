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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;

public interface MutableBeaconStateCapella extends MutableBeaconStateBellatrix, BeaconStateCapella {
  static MutableBeaconStateCapella required(final MutableBeaconState state) {
    return state
        .toMutableVersionCapella()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a capella state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateCapella commitChanges();

  default void setNextWithdrawalIndex(final UInt64 nextWithdrawalIndex) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.NEXT_WITHDRAWAL_INDEX);
    set(fieldIndex, SszUInt64.of(nextWithdrawalIndex));
  }

  default void setNextWithdrawalValidatorIndex(final UInt64 nextWithdrawalValidatorIndex) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.NEXT_WITHDRAWAL_VALIDATOR_INDEX);
    set(fieldIndex, SszUInt64.of(nextWithdrawalValidatorIndex));
  }

  @Override
  default SszMutableList<HistoricalSummary> getHistoricalSummaries() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_SUMMARIES);
    return getAnyByRef(fieldIndex);
  }

  default void setHistoricalSummaries(final SszList<HistoricalSummary> historicalsummaries) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_SUMMARIES);
    set(fieldIndex, historicalsummaries);
  }

  @Override
  default Optional<MutableBeaconStateCapella> toMutableVersionCapella() {
    return Optional.of(this);
  }
}
