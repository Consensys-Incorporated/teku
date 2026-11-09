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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class EpochProcessorAltairTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final EpochProcessorAltair epochProcessor =
      (EpochProcessorAltair) spec.getGenesisSpec().getEpochProcessor();

  @Test
  public void processParticipationUpdates() {
    final BeaconStateAltair randomState = generateRandomState();
    final int validatorCount = randomState.getValidators().size();
    final SszList<SszByte> currentEpochParticipationOrig =
        randomState.getCurrentEpochParticipation();
    final SszList<SszByte> previousEpochParticipationOrig =
        randomState.getPreviousEpochParticipation();

    final BeaconStateAltair updated =
        randomState.updatedAltair(epochProcessor::processParticipationUpdates);

    assertThat(updated.getPreviousEpochParticipation())
        .isNotEqualTo(previousEpochParticipationOrig);
    assertThat(updated.getPreviousEpochParticipation()).isEqualTo(currentEpochParticipationOrig);
    assertThat(updated.getCurrentEpochParticipation().size()).isEqualTo(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      assertThat(updated.getCurrentEpochParticipation().get(i).get()).isEqualTo((byte) 0);
    }
  }

  private BeaconStateAltair generateRandomState() {
    return dataStructureUtil.stateBuilderAltair().build();
  }
}
