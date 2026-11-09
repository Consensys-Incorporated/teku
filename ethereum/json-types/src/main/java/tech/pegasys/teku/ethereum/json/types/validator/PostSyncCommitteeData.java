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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostSyncCommitteeData {
  private int validatorIndex;
  private IntSet syncCommitteeIndices;
  private UInt64 untilEpoch;

  public static final DeserializableTypeDefinition<PostSyncCommitteeData>
      SYNC_COMMITTEE_SUBSCRIPTION =
          DeserializableTypeDefinition.object(PostSyncCommitteeData.class)
              .name("PostSyncCommitteeData")
              .initializer(PostSyncCommitteeData::new)
              .withField(
                  "validator_index",
                  INTEGER_TYPE,
                  PostSyncCommitteeData::getValidatorIndex,
                  PostSyncCommitteeData::setValidatorIndex)
              .withField(
                  "sync_committee_indices",
                  DeserializableTypeDefinition.listOf(INTEGER_TYPE),
                  PostSyncCommitteeData::getSyncCommitteeIndices,
                  PostSyncCommitteeData::setSyncCommitteeIndices)
              .withField(
                  "until_epoch",
                  UINT64_TYPE,
                  PostSyncCommitteeData::getUntilEpoch,
                  PostSyncCommitteeData::setUntilEpoch)
              .build();

  public PostSyncCommitteeData() {}

  public PostSyncCommitteeData(
      final int validatorIndex, final IntSet syncCommitteeIndices, final UInt64 untilEpoch) {
    this.validatorIndex = validatorIndex;
    this.syncCommitteeIndices = syncCommitteeIndices;
    this.untilEpoch = untilEpoch;
  }

  public PostSyncCommitteeData(
      final SyncCommitteeSubnetSubscription syncCommitteeSubnetSubscription) {
    this.validatorIndex = syncCommitteeSubnetSubscription.validatorIndex();
    this.syncCommitteeIndices = syncCommitteeSubnetSubscription.syncCommitteeIndices();
    this.untilEpoch = syncCommitteeSubnetSubscription.untilEpoch();
  }

  public SyncCommitteeSubnetSubscription toSyncCommitteeSubnetSubscription() {
    return new SyncCommitteeSubnetSubscription(validatorIndex, syncCommitteeIndices, untilEpoch);
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public void setValidatorIndex(final int validatorIndex) {
    this.validatorIndex = validatorIndex;
  }

  public List<Integer> getSyncCommitteeIndices() {
    return new IntArrayList(syncCommitteeIndices);
  }

  public void setSyncCommitteeIndices(final List<Integer> syncCommitteeIndices) {
    this.syncCommitteeIndices = new IntOpenHashSet(syncCommitteeIndices);
  }

  public UInt64 getUntilEpoch() {
    return untilEpoch;
  }

  public void setUntilEpoch(final UInt64 untilEpoch) {
    this.untilEpoch = untilEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PostSyncCommitteeData that = (PostSyncCommitteeData) o;
    return validatorIndex == that.validatorIndex
        && Objects.equals(syncCommitteeIndices, that.syncCommitteeIndices)
        && Objects.equals(untilEpoch, that.untilEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validatorIndex, syncCommitteeIndices, untilEpoch);
  }

  @Override
  public String toString() {
    return "PostSyncCommitteeData{"
        + "validatorIndex="
        + validatorIndex
        + ", syncCommitteeIndices="
        + syncCommitteeIndices
        + ", untilEpoch="
        + untilEpoch
        + '}';
  }
}
