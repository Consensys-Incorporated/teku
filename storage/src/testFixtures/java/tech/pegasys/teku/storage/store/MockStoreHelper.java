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

package tech.pegasys.teku.storage.store;

import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class MockStoreHelper {

  public static void mockChainData(
      final UpdatableStore store, final List<SignedBlockAndState> chainData) {
    for (SignedBlockAndState blockAndState : chainData) {
      when(store.retrieveBlockAndState(blockAndState.getRoot()))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));
      when(store.retrieveStateAndBlockSummary(blockAndState.getRoot()))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));
      when(store.retrieveSignedBlock(blockAndState.getRoot()))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock())));
      when(store.retrieveBlock(blockAndState.getRoot()))
          .thenReturn(
              SafeFuture.completedFuture(Optional.of(blockAndState.getBlock().getMessage())));
      when(store.retrieveBlockState(blockAndState.getRoot()))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getState())));
    }
  }

  public static void mockGenesis(
      final Spec spec, final UpdatableStore store, final SignedBlockAndState genesis) {
    mockChainData(store, List.of(genesis));
    final Checkpoint genesisCheckpoint = new Checkpoint(GENESIS_EPOCH, genesis.getRoot());
    when(store.getJustifiedCheckpoint()).thenReturn(genesisCheckpoint);
    when(store.getFinalizedCheckpoint()).thenReturn(genesisCheckpoint);
    when(store.getBestJustifiedCheckpoint()).thenReturn(genesisCheckpoint);
    when(store.getLatestFinalized())
        .thenReturn(AnchorPoint.create(spec, genesisCheckpoint, genesis));
  }
}
