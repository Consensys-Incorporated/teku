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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ShufflingDependentRootUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void usesInclusionListEpochAtProposalEpochBoundary() {
    final Bytes32 previousDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDependentRoot = dataStructureUtil.randomBytes32();
    final BeaconState state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(64))
            .updated(
                mutableState -> {
                  mutableState.getBlockRoots().setElement(47, previousDependentRoot);
                  mutableState.getBlockRoots().setElement(55, currentDependentRoot);
                });

    assertThat(
            ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(63)))
        .isEqualTo(previousDependentRoot);
    assertThat(
            ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(64)))
        .isEqualTo(currentDependentRoot);
  }

  @Test
  void usesGenesisRootForInitialEpochs() {
    final Bytes32 genesisRoot = dataStructureUtil.randomBytes32();
    final BeaconState state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(8))
            .updated(mutableState -> mutableState.getBlockRoots().setElement(0, genesisRoot));

    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.ZERO))
        .isEqualTo(genesisRoot);
    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(8)))
        .isEqualTo(genesisRoot);
  }

  @Test
  void usesLatestHeaderWhenStateIsAtGenesis() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.ZERO);

    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.ZERO))
        .isEqualTo(BeaconBlockHeader.fromState(state).getRoot());
  }
}
