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

public class HeadEvent extends Event<HeadEvent.HeadData> {

  static final SerializableTypeDefinition<HeadData> HEAD_EVENT_TYPE =
      SerializableTypeDefinition.object(HeadData.class)
          .name("HeadEvent")
          .withField("slot", UINT64_TYPE, HeadData::getSlot)
          .withField("block", BYTES32_TYPE, HeadData::getBlock)
          .withField("state", BYTES32_TYPE, HeadData::getState)
          .withField("epoch_transition", BOOLEAN_TYPE, HeadData::isEpochTransition)
          .withField(
              "previous_duty_dependent_root", BYTES32_TYPE, HeadData::getPreviousDutyDependentRoot)
          .withField(
              "current_duty_dependent_root", BYTES32_TYPE, HeadData::getCurrentDutyDependentRoot)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, HeadData::isExecutionOptimistic)
          .build();

  HeadEvent(
      final UInt64 slot,
      final Bytes32 block,
      final Bytes32 state,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot) {
    super(
        HEAD_EVENT_TYPE,
        new HeadData(
            slot,
            block,
            state,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot));
  }

  public static class HeadData {
    private final UInt64 slot;
    private final Bytes32 block;
    private final Bytes32 state;
    private final boolean epochTransition;
    private final boolean executionOptimistic;
    private final Bytes32 previousDutyDependentRoot;
    private final Bytes32 currentDutyDependentRoot;

    HeadData(
        final UInt64 slot,
        final Bytes32 block,
        final Bytes32 state,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot) {
      this.slot = slot;
      this.block = block;
      this.state = state;
      this.epochTransition = epochTransition;
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public Bytes32 getBlock() {
      return block;
    }

    public Bytes32 getState() {
      return state;
    }

    public boolean isEpochTransition() {
      return epochTransition;
    }

    public boolean isExecutionOptimistic() {
      return executionOptimistic;
    }

    public Bytes32 getPreviousDutyDependentRoot() {
      return previousDutyDependentRoot;
    }

    public Bytes32 getCurrentDutyDependentRoot() {
      return currentDutyDependentRoot;
    }
  }
}
