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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.HeadV2Event.Data;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.HeadV2Event.HeadV2Data;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;

public class HeadV2Event extends Event<HeadV2Data> {

  static final SerializableTypeDefinition<Data> HEAD_V2_DATA_TYPE =
      SerializableTypeDefinition.object(Data.class)
          .name("HeadV2EventData")
          .withField("slot", UINT64_TYPE, Data::slot)
          .withField("block", BYTES32_TYPE, Data::block)
          .withField("state", BYTES32_TYPE, Data::state)
          .withField("payload_status", STRING_TYPE, Data::payloadStatus)
          .withField("epoch_transition", BOOLEAN_TYPE, Data::epochTransition)
          .withField("current_epoch_dependent_root", BYTES32_TYPE, Data::currentEpochDependentRoot)
          .withField("next_epoch_dependent_root", BYTES32_TYPE, Data::nextEpochDependentRoot)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, Data::executionOptimistic)
          .build();

  static final SerializableTypeDefinition<HeadV2Data> HEAD_V2_EVENT_TYPE =
      SerializableTypeDefinition.object(HeadV2Data.class)
          .name("HeadV2Event")
          .withField("version", MILESTONE_TYPE, HeadV2Data::milestone)
          .withField("data", HEAD_V2_DATA_TYPE, HeadV2Data::data)
          .build();

  private HeadV2Event(final HeadV2Data data) {
    super(HEAD_V2_EVENT_TYPE, data);
  }

  record HeadV2Data(SpecMilestone milestone, Data data) {}

  record Data(
      UInt64 slot,
      Bytes32 block,
      Bytes32 state,
      String payloadStatus,
      boolean epochTransition,
      boolean executionOptimistic,
      Bytes32 currentEpochDependentRoot,
      Bytes32 nextEpochDependentRoot) {}

  static HeadV2Event create(
      final SpecMilestone milestone,
      final UInt64 slot,
      final Bytes32 block,
      final Bytes32 state,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 currentEpochDependentRoot,
      final Bytes32 nextEpochDependentRoot,
      final ForkChoicePayloadStatus payloadStatus) {
    return new HeadV2Event(
        new HeadV2Data(
            milestone,
            new Data(
                slot,
                block,
                state,
                toApiPayloadStatus(milestone, payloadStatus),
                epochTransition,
                executionOptimistic,
                currentEpochDependentRoot,
                nextEpochDependentRoot)));
  }

  private static String toApiPayloadStatus(
      final SpecMilestone milestone, final ForkChoicePayloadStatus payloadStatus) {
    if (milestone.isLessThan(SpecMilestone.GLOAS)) {
      // Before Gloas a block always carries its execution payload, so there is no empty variant of
      // a head. Fork choice still models every block as a base (pending) node, which is not a
      // payload status the API can report, so the head is always reported as full.
      return "full";
    }
    return switch (payloadStatus) {
      case PAYLOAD_STATUS_EMPTY -> "empty";
      case PAYLOAD_STATUS_FULL -> "full";
      case PAYLOAD_STATUS_PENDING -> "pending";
    };
  }
}
