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

package tech.pegasys.teku.validator.remote.eventsource;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

record ExecutionPayloadAvailableEvent(UInt64 slot, Bytes32 blockRoot) {

  static final DeserializableTypeDefinition<ExecutionPayloadAvailableEvent> TYPE_DEFINITION =
      DeserializableTypeDefinition.object(ExecutionPayloadAvailableEvent.class, Builder.class)
          .initializer(Builder::new)
          .finisher(Builder::build)
          .withField("slot", UINT64_TYPE, ExecutionPayloadAvailableEvent::slot, Builder::slot)
          .withField(
              "block_root",
              BYTES32_TYPE,
              ExecutionPayloadAvailableEvent::blockRoot,
              Builder::blockRoot)
          .build();

  private static class Builder {
    private UInt64 slot;
    private Bytes32 blockRoot;

    Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    Builder blockRoot(final Bytes32 blockRoot) {
      this.blockRoot = blockRoot;
      return this;
    }

    ExecutionPayloadAvailableEvent build() {
      return new ExecutionPayloadAvailableEvent(slot, blockRoot);
    }
  }
}
