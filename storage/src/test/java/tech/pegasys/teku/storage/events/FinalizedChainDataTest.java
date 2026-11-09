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

package tech.pegasys.teku.storage.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.FinalizedChainData;

public class FinalizedChainDataTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final Checkpoint genesisCheckpoint =
      chainBuilder.getCurrentCheckpointForEpoch(GENESIS_EPOCH);
  private final AnchorPoint genesisAnchor = AnchorPoint.fromInitialBlockAndState(spec, genesis);

  @Test
  public void build_withSingleFinalizedBlock() {
    final FinalizedChainData result =
        FinalizedChainData.builder().latestFinalized(genesisAnchor).build();

    assertThat(result.getFinalizedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(result.getLatestFinalizedState()).isEqualTo(genesis.getState());
    assertThat(result.getBlocks()).isEqualTo(Map.of(genesis.getRoot(), genesis.getBlock()));
    assertThat(result.getStates()).isEqualTo(Map.of(genesis.getRoot(), genesis.getState()));
    assertThat(result.getFinalizedChildToParentMap())
        .isEqualTo(Map.of(genesis.getRoot(), genesis.getParentRoot()));
    assertThat(result.getFinalizedExecutionPayloadBoundaryBlock()).isEmpty();
  }

  @Test
  public void build_withFinalizedExecutionPayloadBoundaryBlock() {
    final BlockAndCheckpoints boundaryBlock = BlockAndCheckpoints.fromBlockAndState(spec, genesis);

    final FinalizedChainData result =
        FinalizedChainData.builder()
            .latestFinalized(genesisAnchor)
            .finalizedExecutionPayloadBoundaryBlock(boundaryBlock)
            .build();

    assertThat(result.getFinalizedExecutionPayloadBoundaryBlock()).contains(boundaryBlock);
  }

  @Test
  public void build_shouldRejectFinalizedExecutionPayloadBoundaryBlockForDifferentRoot() {
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(1);
    final BlockAndCheckpoints boundaryBlock = BlockAndCheckpoints.fromBlockAndState(spec, block);

    assertThatThrownBy(
            () ->
                FinalizedChainData.builder()
                    .latestFinalized(genesisAnchor)
                    .finalizedExecutionPayloadBoundaryBlock(boundaryBlock)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("boundary must match latest finalized root");
  }
}
