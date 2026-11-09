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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

record HeadEvent(
    UInt64 slot,
    Bytes32 block,
    Bytes32 state,
    boolean epochTransition,
    Bytes32 previousDutyDependentRoot,
    Bytes32 currentDutyDependentRoot,
    Boolean executionOptimistic) {

  static final DeserializableTypeDefinition<HeadEvent> TYPE_DEFINITION =
      DeserializableTypeDefinition.object(HeadEvent.class, HeadEventBuilder.class)
          .initializer(HeadEventBuilder::new)
          .finisher(HeadEventBuilder::build)
          .withField("slot", UINT64_TYPE, HeadEvent::slot, HeadEventBuilder::slot)
          .withField("block", BYTES32_TYPE, HeadEvent::block, HeadEventBuilder::block)
          .withField("state", BYTES32_TYPE, HeadEvent::state, HeadEventBuilder::state)
          .withField(
              "epoch_transition",
              BOOLEAN_TYPE,
              HeadEvent::epochTransition,
              HeadEventBuilder::epochTransition)
          .withField(
              "previous_duty_dependent_root",
              BYTES32_TYPE,
              HeadEvent::previousDutyDependentRoot,
              HeadEventBuilder::previousDutyDependentRoot)
          .withField(
              "current_duty_dependent_root",
              BYTES32_TYPE,
              HeadEvent::currentDutyDependentRoot,
              HeadEventBuilder::currentDutyDependentRoot)
          .withField(
              "execution_optimistic",
              BOOLEAN_TYPE,
              HeadEvent::executionOptimistic,
              HeadEventBuilder::executionOptimistic)
          .build();

  private static class HeadEventBuilder {
    private UInt64 slot;
    private Bytes32 block;
    private Bytes32 state;
    private boolean epochTransition;
    private Bytes32 previousDutyDependentRoot;
    private Bytes32 currentDutyDependentRoot;
    private boolean executionOptimistic;

    HeadEventBuilder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    HeadEventBuilder block(final Bytes32 block) {
      this.block = block;
      return this;
    }

    HeadEventBuilder state(final Bytes32 state) {
      this.state = state;
      return this;
    }

    HeadEventBuilder epochTransition(final boolean epochTransition) {
      this.epochTransition = epochTransition;
      return this;
    }

    HeadEventBuilder previousDutyDependentRoot(final Bytes32 previousDutyDependentRoot) {
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      return this;
    }

    HeadEventBuilder currentDutyDependentRoot(final Bytes32 currentDutyDependentRoot) {
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      return this;
    }

    HeadEventBuilder executionOptimistic(final boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    HeadEvent build() {
      return new HeadEvent(
          slot,
          block,
          state,
          epochTransition,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          executionOptimistic);
    }
  }
}
