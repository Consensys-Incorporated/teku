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

package tech.pegasys.teku.validator.remote.eventsource;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

record HeadV2Event(
    UInt64 slot,
    Bytes32 block,
    Bytes32 state,
    boolean epochTransition,
    Bytes32 previousDutyDependentRoot,
    Bytes32 currentDutyDependentRoot,
    Boolean executionOptimistic,
    String payloadStatus) {

  static final DeserializableTypeDefinition<HeadV2Event> TYPE_DEFINITION =
      DeserializableTypeDefinition.object(HeadV2Event.class, Builder.class)
          .initializer(Builder::new)
          .finisher(Builder::build)
          .withField("slot", UINT64_TYPE, HeadV2Event::slot, Builder::slot)
          .withField("block", BYTES32_TYPE, HeadV2Event::block, Builder::block)
          .withField("state", BYTES32_TYPE, HeadV2Event::state, Builder::state)
          .withField(
              "epoch_transition",
              BOOLEAN_TYPE,
              HeadV2Event::epochTransition,
              Builder::epochTransition)
          .withField(
              "previous_duty_dependent_root",
              BYTES32_TYPE,
              HeadV2Event::previousDutyDependentRoot,
              Builder::previousDutyDependentRoot)
          .withField(
              "current_duty_dependent_root",
              BYTES32_TYPE,
              HeadV2Event::currentDutyDependentRoot,
              Builder::currentDutyDependentRoot)
          .withField(
              "execution_optimistic",
              BOOLEAN_TYPE,
              HeadV2Event::executionOptimistic,
              Builder::executionOptimistic)
          .withField(
              "payload_status", STRING_TYPE, HeadV2Event::payloadStatus, Builder::payloadStatus)
          .build();

  private static class Builder {
    private UInt64 slot;
    private Bytes32 block;
    private Bytes32 state;
    private boolean epochTransition;
    private Bytes32 previousDutyDependentRoot;
    private Bytes32 currentDutyDependentRoot;
    private boolean executionOptimistic;
    private String payloadStatus;

    Builder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    Builder block(final Bytes32 block) {
      this.block = block;
      return this;
    }

    Builder state(final Bytes32 state) {
      this.state = state;
      return this;
    }

    Builder epochTransition(final boolean epochTransition) {
      this.epochTransition = epochTransition;
      return this;
    }

    Builder previousDutyDependentRoot(final Bytes32 previousDutyDependentRoot) {
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      return this;
    }

    Builder currentDutyDependentRoot(final Bytes32 currentDutyDependentRoot) {
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      return this;
    }

    Builder executionOptimistic(final boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    Builder payloadStatus(final String payloadStatus) {
      this.payloadStatus = payloadStatus;
      return this;
    }

    HeadV2Event build() {
      return new HeadV2Event(
          slot,
          block,
          state,
          epochTransition,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          executionOptimistic,
          payloadStatus);
    }
  }
}
