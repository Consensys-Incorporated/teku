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

public class ChainReorgEvent extends Event<ChainReorgEvent.ChainReorgData> {

  public static final SerializableTypeDefinition<ChainReorgData> CHAIN_REORG_EVENT_TYPE =
      SerializableTypeDefinition.object(ChainReorgData.class)
          .name("ChainReorgEvent")
          .withField("slot", UINT64_TYPE, ChainReorgData::getSlot)
          .withField("depth", UINT64_TYPE, ChainReorgData::getDepth)
          .withField("old_head_block", BYTES32_TYPE, ChainReorgData::getOldHeadBlock)
          .withField("new_head_block", BYTES32_TYPE, ChainReorgData::getNewHeadBlock)
          .withField("old_head_state", BYTES32_TYPE, ChainReorgData::getOldHeadState)
          .withField("new_head_state", BYTES32_TYPE, ChainReorgData::getNewHeadState)
          .withField("epoch", UINT64_TYPE, ChainReorgData::getEpoch)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ChainReorgData::isExecutionOptimistic)
          .build();

  ChainReorgEvent(
      final UInt64 slot,
      final UInt64 depth,
      final Bytes32 oldHeadBlock,
      final Bytes32 newHeadBlock,
      final Bytes32 oldHeadState,
      final Bytes32 newHeadState,
      final UInt64 epoch,
      final boolean executionOptimistic) {
    super(
        CHAIN_REORG_EVENT_TYPE,
        new ChainReorgData(
            slot,
            depth,
            oldHeadBlock,
            newHeadBlock,
            oldHeadState,
            newHeadState,
            epoch,
            executionOptimistic));
  }

  public static class ChainReorgData {
    private final UInt64 slot;
    private final UInt64 depth;
    private final Bytes32 oldHeadBlock;
    private final Bytes32 newHeadBlock;
    private final Bytes32 oldHeadState;
    private final Bytes32 newHeadState;
    private final UInt64 epoch;
    private final boolean executionOptimistic;

    ChainReorgData(
        final UInt64 slot,
        final UInt64 depth,
        final Bytes32 oldHeadBlock,
        final Bytes32 newHeadBlock,
        final Bytes32 oldHeadState,
        final Bytes32 newHeadState,
        final UInt64 epoch,
        final boolean executionOptimistic) {
      this.slot = slot;
      this.depth = depth;
      this.oldHeadBlock = oldHeadBlock;
      this.newHeadBlock = newHeadBlock;
      this.oldHeadState = oldHeadState;
      this.newHeadState = newHeadState;
      this.epoch = epoch;
      this.executionOptimistic = executionOptimistic;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public UInt64 getDepth() {
      return depth;
    }

    public Bytes32 getOldHeadBlock() {
      return oldHeadBlock;
    }

    public Bytes32 getNewHeadBlock() {
      return newHeadBlock;
    }

    public Bytes32 getOldHeadState() {
      return oldHeadState;
    }

    public Bytes32 getNewHeadState() {
      return newHeadState;
    }

    public UInt64 getEpoch() {
      return epoch;
    }

    public boolean isExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
