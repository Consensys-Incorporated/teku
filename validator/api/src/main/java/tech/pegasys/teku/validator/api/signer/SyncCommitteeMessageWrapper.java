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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SyncCommitteeMessageWrapper(Bytes32 blockRoot, UInt64 slot) {

  public static DeserializableTypeDefinition<SyncCommitteeMessageWrapper> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(SyncCommitteeMessageWrapper.class, Builder.class)
        .initializer(Builder::new)
        .finisher(Builder::build)
        .withField("slot", UINT64_TYPE, SyncCommitteeMessageWrapper::slot, Builder::slot)
        .withField(
            "beacon_block_root",
            BYTES32_TYPE,
            SyncCommitteeMessageWrapper::blockRoot,
            Builder::blockRoot)
        .build();
  }

  static class Builder {
    private Bytes32 blockRoot;
    private UInt64 slot;

    Builder blockRoot(final Bytes32 blockRoot) {
      this.blockRoot = blockRoot;
      return this;
    }

    Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    SyncCommitteeMessageWrapper build() {
      return new SyncCommitteeMessageWrapper(blockRoot, slot);
    }
  }
}
