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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class FinalizedCheckpointEvent
    extends Event<FinalizedCheckpointEvent.FinalizedCheckpointData> {

  static final SerializableTypeDefinition<FinalizedCheckpointData> FINALIZED_CHECKPOINT_EVENT_TYPE =
      SerializableTypeDefinition.object(FinalizedCheckpointData.class)
          .name("FinalizedCheckpointEvent")
          .withField("block", BYTES32_TYPE, FinalizedCheckpointData::getBlock)
          .withField("state", BYTES32_TYPE, FinalizedCheckpointData::getState)
          .withField("epoch", UINT64_TYPE, FinalizedCheckpointData::getEpoch)
          .withField(
              EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, FinalizedCheckpointData::isExecutionOptimistic)
          .build();

  FinalizedCheckpointEvent(
      final Bytes32 block,
      final Bytes32 state,
      final UInt64 epoch,
      final boolean executionOptimistic) {
    super(
        FINALIZED_CHECKPOINT_EVENT_TYPE,
        new FinalizedCheckpointData(block, state, epoch, executionOptimistic));
  }

  public static class FinalizedCheckpointData {
    public final Bytes32 block;
    public final Bytes32 state;
    public final UInt64 epoch;
    public final boolean executionOptimistic;

    FinalizedCheckpointData(
        final Bytes32 block,
        final Bytes32 state,
        final UInt64 epoch,
        final boolean executionOptimistic) {
      this.block = block;
      this.state = state;
      this.epoch = epoch;
      this.executionOptimistic = executionOptimistic;
    }

    private Bytes32 getBlock() {
      return block;
    }

    private Bytes32 getState() {
      return state;
    }

    private UInt64 getEpoch() {
      return epoch;
    }

    private boolean isExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
