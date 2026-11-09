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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadAvailableEvent extends Event<ExecutionPayloadAvailableEvent.Data> {

  public static final SerializableTypeDefinition<ExecutionPayloadAvailableEvent.Data>
      EXECUTION_PAYLOAD_AVAILABLE_EVENT_TYPE =
          SerializableTypeDefinition.object(ExecutionPayloadAvailableEvent.Data.class)
              .name("ExecutionPayloadAvailableEvent")
              .withField(SLOT, UINT64_TYPE, ExecutionPayloadAvailableEvent.Data::slot)
              .withField(BLOCK_ROOT, BYTES32_TYPE, ExecutionPayloadAvailableEvent.Data::blockRoot)
              .build();

  public ExecutionPayloadAvailableEvent(final UInt64 slot, final Bytes32 blockRoot) {
    super(EXECUTION_PAYLOAD_AVAILABLE_EVENT_TYPE, new Data(slot, blockRoot));
  }

  public record Data(UInt64 slot, Bytes32 blockRoot) {}
}
