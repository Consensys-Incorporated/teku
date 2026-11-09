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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class EventTest {
  private final OpenApiTestUtil<EventTest> util = new OpenApiTestUtil<>(EventTest.class);

  @ParameterizedTest(name = "{0}")
  @MethodSource("getEventTypes")
  void shouldHaveConsistentSchema(final String testName, final OpenApiTypeDefinition apiDefinition)
      throws JsonProcessingException {
    util.compareToKnownDefinition(apiDefinition);
  }

  public static Stream<Arguments> getEventTypes() {

    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Spec specElectra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtilElectra = new DataStructureUtil(specElectra);
    return Stream.of(
        Arguments.of(
            "AttestationEvent Phase0",
            new AttestationEvent(dataStructureUtil.randomAttestation()).getJsonTypeDefinition()),
        Arguments.of(
            "AttestationEvent Electra",
            new AttestationEvent(dataStructureUtilElectra.randomAttestation())
                .getJsonTypeDefinition()),
        Arguments.of(
            "BlockEvent",
            new BlockEvent(dataStructureUtil.randomSignedBeaconBlock(1), false)
                .getJsonTypeDefinition()),
        Arguments.of("ChainReorgEvent", ChainReorgEvent.CHAIN_REORG_EVENT_TYPE),
        Arguments.of(
            "FinalizedCheckpointEvent", FinalizedCheckpointEvent.FINALIZED_CHECKPOINT_EVENT_TYPE),
        Arguments.of("HeadEvent", HeadEvent.HEAD_EVENT_TYPE),
        Arguments.of("HeadV2Event", HeadV2Event.HEAD_V2_EVENT_TYPE),
        Arguments.of("SyncStateChangeEvent", SyncStateChangeEvent.SYNC_STATE_CHANGE_EVENT_TYPE),
        Arguments.of(
            "VoluntaryExitEvent",
            new VoluntaryExitEvent(dataStructureUtil.randomSignedVoluntaryExit())
                .getJsonTypeDefinition()));
  }
}
