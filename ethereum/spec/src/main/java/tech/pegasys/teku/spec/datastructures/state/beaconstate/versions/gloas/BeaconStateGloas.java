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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDERS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PENDING_PAYMENTS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PENDING_WITHDRAWALS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EXECUTION_PAYLOAD_AVAILABILITY;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_EXECUTION_PAYLOAD_BID;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.NEXT_WITHDRAWAL_BUILDER_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PAYLOAD_EXPECTED_WITHDRAWALS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PTC_WINDOW;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;

public interface BeaconStateGloas extends BeaconStateFulu {
  static BeaconStateGloas required(final BeaconState state) {
    return state
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a Gloas state but got: " + state.getClass().getSimpleName()));
  }

  // `latest_execution_payload_header` has been removed in Gloas
  @Override
  default Optional<ExecutionPayloadHeader> getLatestExecutionPayloadHeader() {
    return Optional.empty();
  }

  @SuppressWarnings("unused")
  private static <T extends SszData> void addItems(
      final MoreObjects.ToStringHelper stringBuilder,
      final String keyPrefix,
      final SszCollection<T> items) {
    for (int i = 0; i < items.size(); i++) {
      stringBuilder.add(keyPrefix + "[" + i + "]", items.get(i));
    }
  }

  static void describeCustomGloasFields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateGloas state) {
    BeaconStateFulu.describeCustomFuluFields(stringBuilder, state);
    stringBuilder.add("latest_block_hash", state.getLatestBlockHash());
    addItems(stringBuilder, "builders", state.getBuilders());
    stringBuilder.add("next_withdrawal_builder_index", state.getNextWithdrawalBuilderIndex());
    addItems(
        stringBuilder, "execution_payload_availability", state.getExecutionPayloadAvailability());
    addItems(stringBuilder, "builder_pending_payments", state.getBuilderPendingPayments());
    addItems(stringBuilder, "builder_pending_withdrawals", state.getBuilderPendingWithdrawals());
    stringBuilder.add("latest_execution_payload_bid", state.getLatestExecutionPayloadBid());
    stringBuilder.add("payload_expected_withdrawals", state.getPayloadExpectedWithdrawals());
    stringBuilder.add("ptc_window", state.getPtcWindow());
  }

  @Override
  MutableBeaconStateGloas createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateGloas updatedGloas(final Mutator<MutableBeaconStateGloas, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStateGloas writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateGloas> toVersionGloas() {
    return Optional.of(this);
  }

  default ExecutionPayloadBid getLatestExecutionPayloadBid() {
    final int index = getSchema().getFieldIndex(LATEST_EXECUTION_PAYLOAD_BID);
    return getAny(index);
  }

  default SszList<Builder> getBuilders() {
    final int index = getSchema().getFieldIndex(BUILDERS);
    return getAny(index);
  }

  default UInt64 getNextWithdrawalBuilderIndex() {
    final int index = getSchema().getFieldIndex(NEXT_WITHDRAWAL_BUILDER_INDEX);
    return ((SszUInt64) getAny(index)).get();
  }

  default SszBitvector getExecutionPayloadAvailability() {
    final int index = getSchema().getFieldIndex(EXECUTION_PAYLOAD_AVAILABILITY);
    return getAny(index);
  }

  default SszVector<BuilderPendingPayment> getBuilderPendingPayments() {
    final int index = getSchema().getFieldIndex(BUILDER_PENDING_PAYMENTS);
    return getAny(index);
  }

  default SszList<BuilderPendingWithdrawal> getBuilderPendingWithdrawals() {
    final int index = getSchema().getFieldIndex(BUILDER_PENDING_WITHDRAWALS);
    return getAny(index);
  }

  default Bytes32 getLatestBlockHash() {
    final int index = getSchema().getFieldIndex(LATEST_BLOCK_HASH);
    return ((SszBytes32) getAny(index)).get();
  }

  default SszList<Withdrawal> getPayloadExpectedWithdrawals() {
    final int index = getSchema().getFieldIndex(PAYLOAD_EXPECTED_WITHDRAWALS);
    return getAny(index);
  }

  default SszVector<SszUInt64Vector> getPtcWindow() {
    final int index = getSchema().getFieldIndex(PTC_WINDOW);
    return getAny(index);
  }
}
