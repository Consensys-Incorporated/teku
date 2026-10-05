/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.validator;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SubnetSubscription(int subnetId, UInt64 unsubscriptionSlot)
    implements Comparable<SubnetSubscription> {

  public static final DeserializableTypeDefinition<SubnetSubscription> SSZ_DATA =
      DeserializableTypeDefinition.object(SubnetSubscription.class, SubnetSubscriptionBuilder.class)
          .initializer(SubnetSubscriptionBuilder::new)
          .withField(
              "subnet_id",
              INTEGER_TYPE,
              SubnetSubscription::subnetId,
              SubnetSubscriptionBuilder::subnetId)
          .withField(
              "unsubscription_slot",
              UINT64_TYPE,
              SubnetSubscription::unsubscriptionSlot,
              SubnetSubscriptionBuilder::unsubscriptionSlot)
          .finisher(SubnetSubscriptionBuilder::build)
          .build();

  @Override
  public int compareTo(@NotNull final SubnetSubscription o) {
    return Comparator.comparing(SubnetSubscription::unsubscriptionSlot)
        .thenComparingInt(SubnetSubscription::subnetId)
        .compare(this, o);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subnetId", subnetId)
        .add("unsubscriptionSlot", unsubscriptionSlot)
        .toString();
  }

  static class SubnetSubscriptionBuilder {
    private int subnetId;
    private UInt64 unsubscriptionSlot;

    SubnetSubscriptionBuilder() {}

    public SubnetSubscriptionBuilder subnetId(final int subnetId) {
      this.subnetId = subnetId;
      return this;
    }

    public SubnetSubscriptionBuilder unsubscriptionSlot(final UInt64 unsubscriptionSlot) {
      this.unsubscriptionSlot = unsubscriptionSlot;
      return this;
    }

    SubnetSubscription build() {
      return new SubnetSubscription(subnetId, unsubscriptionSlot);
    }
  }
}
