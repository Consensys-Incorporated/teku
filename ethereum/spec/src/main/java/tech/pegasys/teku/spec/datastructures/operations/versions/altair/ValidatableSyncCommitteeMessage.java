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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;

public class ValidatableSyncCommitteeMessage {
  private final SyncCommitteeMessage message;
  private final OptionalInt receivedSubnetId;
  private final Supplier<Bytes32> hashTreeRoot;

  private volatile Optional<SyncSubcommitteeAssignments> subcommitteeAssignments = Optional.empty();

  private ValidatableSyncCommitteeMessage(
      final SyncCommitteeMessage message, final OptionalInt receivedSubnetId) {
    this.message = message;
    this.receivedSubnetId = receivedSubnetId;
    this.hashTreeRoot = Suppliers.memoize(message::hashTreeRoot);
  }

  public static ValidatableSyncCommitteeMessage fromValidator(final SyncCommitteeMessage message) {
    return new ValidatableSyncCommitteeMessage(message, OptionalInt.empty());
  }

  public static ValidatableSyncCommitteeMessage fromNetwork(
      final SyncCommitteeMessage message, final int receivedSubnetId) {
    return new ValidatableSyncCommitteeMessage(message, OptionalInt.of(receivedSubnetId));
  }

  public SyncCommitteeMessage getMessage() {
    return message;
  }

  public OptionalInt getReceivedSubnetId() {
    return receivedSubnetId;
  }

  public Optional<SyncSubcommitteeAssignments> getSubcommitteeAssignments() {
    return subcommitteeAssignments;
  }

  public SyncSubcommitteeAssignments calculateAssignments(
      final Spec spec, final BeaconState state) {
    final Optional<SyncSubcommitteeAssignments> currentValue = this.subcommitteeAssignments;
    if (currentValue.isPresent()) {
      return currentValue.get();
    }
    final UInt64 messageSlot = message.getSlot();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(messageSlot);
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil.getSubcommitteeAssignments(
            state,
            syncCommitteeUtil.getEpochForDutiesAtSlot(messageSlot),
            message.getValidatorIndex());

    this.subcommitteeAssignments = Optional.of(assignments);
    return assignments;
  }

  public Bytes32 hashTreeRoot() {
    return hashTreeRoot.get();
  }

  public UInt64 getEarliestSlotForForkChoiceProcessing() {
    return message.getSlot();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return Sets.newHashSet(message.getBeaconBlockRoot());
  }

  @VisibleForTesting
  public void setSubcommitteeAssignments(final SyncSubcommitteeAssignments assignments) {
    this.subcommitteeAssignments = Optional.of(assignments);
  }

  public UInt64 getSlot() {
    return message.getSlot();
  }

  public Bytes32 getBeaconBlockRoot() {
    return message.getBeaconBlockRoot();
  }
}
