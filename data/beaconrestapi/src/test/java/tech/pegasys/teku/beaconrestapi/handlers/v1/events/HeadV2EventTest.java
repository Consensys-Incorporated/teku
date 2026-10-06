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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class HeadV2EventTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @ParameterizedTest(name = "{0} head with fork choice status {1} is reported as full")
  @MethodSource("preGloasMilestonesAndPayloadStatuses")
  void shouldReportPreGloasHeadsAsFull(
      final SpecMilestone milestone, final ForkChoicePayloadStatus payloadStatus) {
    // before Gloas a block always carries its execution payload, and fork choice reports every
    // node as pending, which is not a payload status the API can report
    assertThat(payloadStatusOf(milestone, payloadStatus)).isEqualTo("full");
  }

  @Test
  void shouldReportGloasHeadWithoutAPayloadAsEmpty() {
    assertThat(payloadStatusOf(SpecMilestone.GLOAS, ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY))
        .isEqualTo("empty");
  }

  @Test
  void shouldReportGloasHeadWithAPayloadAsFull() {
    assertThat(payloadStatusOf(SpecMilestone.GLOAS, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .isEqualTo("full");
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void shouldAlwaysReportAPayloadStatus(final SpecMilestone milestone) {
    assertThat(payloadStatusOf(milestone, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .isNotBlank();
  }

  private String payloadStatusOf(
      final SpecMilestone milestone, final ForkChoicePayloadStatus payloadStatus) {
    return HeadV2Event.create(
            milestone,
            UInt64.valueOf(10),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            false,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            payloadStatus)
        .getData()
        .data()
        .payloadStatus();
  }

  private static Stream<Arguments> preGloasMilestonesAndPayloadStatuses() {
    return SpecMilestone.getAllPriorMilestones(SpecMilestone.GLOAS).stream()
        .flatMap(
            milestone ->
                Stream.of(ForkChoicePayloadStatus.values())
                    .map(payloadStatus -> Arguments.of(milestone, payloadStatus)));
  }
}
