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

record HeadV2Event(Data data) {

  /**
   * { "version": "gloas", "data": { "slot": "10", "block":
   * "0x9a2fefd2fdb57f74993c7780ea5b9030d2897b615b89f808011ca5aebed54eaf", "state":
   * "0x600e852a08c1200654ddf11025f1ceacb3c2e74bdd5c630cde0838b2591b69f9", "payload_status":
   * "empty", "epoch_transition": false, "current_epoch_dependent_root":
   * "0x5e0043f107cb57913498fbf2f99ff55e730bf1e151f02f221e977c91a90a0e91",
   * "next_epoch_dependent_root":
   * "0x5e0043f107cb57913498fbf2f99ff55e730bf1e151f02f221e977c91a90a0e91", "execution_optimistic":
   * false } }
   */
  static final DeserializableTypeDefinition<HeadV2Event.Data> DATA_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(Data.class, DataBuilder.class)
          .initializer(DataBuilder::new)
          .finisher(DataBuilder::build)
          .withField("slot", UINT64_TYPE, Data::slot, DataBuilder::slot)
          .withField("block", BYTES32_TYPE, Data::block, DataBuilder::block)
          .withField("state", BYTES32_TYPE, Data::state, DataBuilder::state)
          .withField(
              "epoch_transition", BOOLEAN_TYPE, Data::epochTransition, DataBuilder::epochTransition)
          .withField(
              "current_epoch_dependent_root",
              BYTES32_TYPE,
              Data::currentEpochDependentRoot,
              DataBuilder::currentEpochDependentRoot)
          .withField(
              "next_epoch_dependent_root",
              BYTES32_TYPE,
              Data::nextEpochDependentRoot,
              DataBuilder::nextEpochDependentRoot)
          .withField(
              "execution_optimistic",
              BOOLEAN_TYPE,
              Data::executionOptimistic,
              DataBuilder::executionOptimistic)
          .withField("payload_status", STRING_TYPE, Data::payloadStatus, DataBuilder::payloadStatus)
          .build();

  static final DeserializableTypeDefinition<HeadV2Event> TYPE_DEFINITION =
      DeserializableTypeDefinition.object(HeadV2Event.class, Builder.class)
          .initializer(Builder::new)
          .finisher(Builder::build)
          .withField("data", DATA_TYPE_DEFINITION, HeadV2Event::data, Builder::data)
          .build();

  private static class Builder {
    private Data data;

    Builder data(final Data data) {
      this.data = data;
      return this;
    }

    HeadV2Event build() {
      return new HeadV2Event(data);
    }
  }

  public record Data(
      UInt64 slot,
      Bytes32 block,
      Bytes32 state,
      boolean epochTransition,
      Bytes32 currentEpochDependentRoot,
      Bytes32 nextEpochDependentRoot,
      Boolean executionOptimistic,
      String payloadStatus) {}

  private static class DataBuilder {
    private UInt64 slot;
    private Bytes32 block;
    private Bytes32 state;
    private boolean epochTransition;
    private Bytes32 currentEpochDependentRoot;
    private Bytes32 nextEpochDependentRoot;
    private boolean executionOptimistic;
    private String payloadStatus;

    DataBuilder slot(final UInt64 slot) {
      this.slot = slot;
      return this;
    }

    DataBuilder block(final Bytes32 block) {
      this.block = block;
      return this;
    }

    DataBuilder state(final Bytes32 state) {
      this.state = state;
      return this;
    }

    DataBuilder epochTransition(final boolean epochTransition) {
      this.epochTransition = epochTransition;
      return this;
    }

    DataBuilder currentEpochDependentRoot(final Bytes32 currentEpochDependentRoot) {
      this.currentEpochDependentRoot = currentEpochDependentRoot;
      return this;
    }

    DataBuilder nextEpochDependentRoot(final Bytes32 nextEpochDependentRoot) {
      this.nextEpochDependentRoot = nextEpochDependentRoot;
      return this;
    }

    DataBuilder executionOptimistic(final boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    DataBuilder payloadStatus(final String payloadStatus) {
      this.payloadStatus = payloadStatus;
      return this;
    }

    Data build() {
      return new Data(
          slot,
          block,
          state,
          epochTransition,
          currentEpochDependentRoot,
          nextEpochDependentRoot,
          executionOptimistic,
          payloadStatus);
    }
  }
}
