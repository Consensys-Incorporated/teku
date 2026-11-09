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

package tech.pegasys.teku.validator.api.signer;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SyncAggregatorSelectionDataWrapper(UInt64 slot, UInt64 subcommitteeIndex) {
  public static DeserializableTypeDefinition<SyncAggregatorSelectionDataWrapper>
      getJsonTypefinition() {
    return DeserializableTypeDefinition.object(
            SyncAggregatorSelectionDataWrapper.class, Builder.class)
        .initializer(Builder::new)
        .finisher(Builder::build)
        .withField("slot", UINT64_TYPE, SyncAggregatorSelectionDataWrapper::slot, Builder::slot)
        .withField(
            "subcommittee_index",
            UINT64_TYPE,
            SyncAggregatorSelectionDataWrapper::subcommitteeIndex,
            Builder::subcommitteeIndex)
        .build();
  }

  static class Builder {
    private UInt64 slot;
    private UInt64 subcommitteeIndex;

    Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    Builder subcommitteeIndex(final UInt64 subcommitteeIndex) {
      this.subcommitteeIndex = subcommitteeIndex;
      return this;
    }

    SyncAggregatorSelectionDataWrapper build() {
      return new SyncAggregatorSelectionDataWrapper(slot, subcommitteeIndex);
    }
  }
}
