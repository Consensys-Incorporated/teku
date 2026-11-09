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

package tech.pegasys.teku.validator.api;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record CommitteeSubscriptionData(
    int validatorIndex,
    int committeeIndex,
    UInt64 committeesAtSlot,
    UInt64 slot,
    boolean isAggregator) {
  public static final DeserializableTypeDefinition<CommitteeSubscriptionData> SSZ_DATA =
      DeserializableTypeDefinition.object(
              CommitteeSubscriptionData.class, CommitteeSubscriptionDataBuilder.class)
          .name("CommitteeSubscriptionData")
          .initializer(CommitteeSubscriptionDataBuilder::new)
          .finisher(CommitteeSubscriptionDataBuilder::build)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              CommitteeSubscriptionData::validatorIndex,
              CommitteeSubscriptionDataBuilder::validatorIndex)
          .withField(
              "committee_index",
              INTEGER_TYPE,
              CommitteeSubscriptionData::committeeIndex,
              CommitteeSubscriptionDataBuilder::committeeIndex)
          .withField(
              "committees_at_slot",
              UINT64_TYPE,
              CommitteeSubscriptionData::committeesAtSlot,
              CommitteeSubscriptionDataBuilder::committeesAtSlot)
          .withField(
              "slot",
              UINT64_TYPE,
              CommitteeSubscriptionData::slot,
              CommitteeSubscriptionDataBuilder::slot)
          .withField(
              "is_aggregator",
              BOOLEAN_TYPE,
              CommitteeSubscriptionData::isAggregator,
              CommitteeSubscriptionDataBuilder::isAggregator)
          .build();

  public static CommitteeSubscriptionData create(
      final CommitteeSubscriptionRequest committeeSubscriptionRequest) {
    return new CommitteeSubscriptionData(
        committeeSubscriptionRequest.getValidatorIndex(),
        committeeSubscriptionRequest.getCommitteeIndex(),
        committeeSubscriptionRequest.getCommitteesAtSlot(),
        committeeSubscriptionRequest.getSlot(),
        committeeSubscriptionRequest.isAggregator());
  }

  public CommitteeSubscriptionRequest toCommitteeSubscriptionRequest() {
    return new CommitteeSubscriptionRequest(
        validatorIndex, committeeIndex, committeesAtSlot, slot, isAggregator);
  }

  @Override
  public String toString() {
    return "CommitteeSubscriptionData{"
        + "validatorIndex="
        + validatorIndex
        + ", committeeIndex="
        + committeeIndex
        + ", committeesAtSlot="
        + committeesAtSlot
        + ", slot="
        + slot
        + ", isAggregator="
        + isAggregator
        + '}';
  }

  static class CommitteeSubscriptionDataBuilder {
    private int validatorIndex;
    private int committeeIndex;
    private UInt64 committeesAtSlot;
    private UInt64 slot;
    private boolean isAggregator;

    public CommitteeSubscriptionDataBuilder validatorIndex(final int validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public CommitteeSubscriptionDataBuilder committeeIndex(final int committeeIndex) {
      this.committeeIndex = committeeIndex;
      return this;
    }

    public CommitteeSubscriptionDataBuilder committeesAtSlot(final UInt64 committeesAtSlot) {
      this.committeesAtSlot = committeesAtSlot;
      return this;
    }

    public CommitteeSubscriptionDataBuilder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    public CommitteeSubscriptionDataBuilder isAggregator(final boolean isAggregator) {
      this.isAggregator = isAggregator;
      return this;
    }

    public CommitteeSubscriptionData build() {
      return new CommitteeSubscriptionData(
          validatorIndex, committeeIndex, committeesAtSlot, slot, isAggregator);
    }
  }
}
