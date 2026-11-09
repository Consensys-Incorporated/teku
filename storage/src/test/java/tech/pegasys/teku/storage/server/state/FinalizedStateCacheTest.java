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

package tech.pegasys.teku.storage.server.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.Database;

class FinalizedStateCacheTest {
  private static final int MAXIMUM_CACHE_SIZE = 3;
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
  private final Database database = mock(Database.class);
  // We don't use soft references in unit tests to avoid intermittency
  private final FinalizedStateCache cache =
      new FinalizedStateCache(spec, database, MAXIMUM_CACHE_SIZE, false, 120);

  @BeforeEach
  public void setUp() {
    chainBuilder.generateGenesis();
  }

  @Test
  void shouldUseCachedStateWhenAvailable() throws Exception {
    final BeaconState state = chainBuilder.generateBlockAtSlot(ONE).getState();
    when(database.getLatestAvailableFinalizedState(state.getSlot())).thenReturn(Optional.of(state));

    final Optional<BeaconState> initialResult = cache.getFinalizedState(state.getSlot());
    assertThat(initialResult).contains(state);

    assertThat(cache.getFinalizedState(state.getSlot())).contains(state);
    verify(database, times(1)).getLatestAvailableFinalizedState(state.getSlot());
    verifyNoMoreInteractions(database);
  }

  @Test
  void shouldRegenerateFromMoreRecentCachedState() throws Exception {
    final UInt64 databaseSlot = UInt64.valueOf(1);
    final UInt64 cachedSlot = UInt64.valueOf(2);
    final UInt64 requestedSlot = UInt64.valueOf(3);
    chainBuilder.generateBlocksUpToSlot(requestedSlot);

    // Latest state available from the database is at databaseSlot (1)
    when(database.getLatestAvailableFinalizedState(any()))
        .thenReturn(Optional.of(chainBuilder.getStateAtSlot(databaseSlot)));
    allowStreamingBlocks();

    // Should regenerate the same state
    assertThat(cache.getFinalizedState(cachedSlot))
        .contains(chainBuilder.getStateAtSlot(cachedSlot));
    verify(database).streamFinalizedBlocks(databaseSlot.plus(ONE), cachedSlot);

    // Should only need the blocks from the cached state forward
    assertThat(cache.getFinalizedState(requestedSlot))
        .contains(chainBuilder.getStateAtSlot(requestedSlot));
    verify(database).streamFinalizedBlocks(cachedSlot.plus(ONE), requestedSlot);
  }

  @Test
  void shouldLimitNumberOfCachedStates() throws Exception {
    chainBuilder.generateBlocksUpToSlot(MAXIMUM_CACHE_SIZE + 1);
    when(database.getLatestAvailableFinalizedState(any()))
        .thenReturn(Optional.of(chainBuilder.getGenesis().getState()));
    allowStreamingBlocks();

    // Fill up the cache
    for (int i = 1; i <= MAXIMUM_CACHE_SIZE; i++) {
      cache.getFinalizedState(UInt64.valueOf(i));
    }
    // Evict the least recently used item (should be genesis state)
    cache.getFinalizedState(UInt64.valueOf(MAXIMUM_CACHE_SIZE + 1));

    cache.getFinalizedState(ONE);
    verify(database, times(2)).streamFinalizedBlocks(ONE, ONE);
  }

  @Test
  void shouldReturnEmptyWhenStateIsNotAvailable() {
    when(database.getLatestAvailableFinalizedState(any())).thenReturn(Optional.empty());

    assertThat(cache.getFinalizedState(ONE)).isEmpty();
  }

  private void allowStreamingBlocks() {
    when(database.streamFinalizedBlocks(any(), any()))
        .thenAnswer(
            invocation ->
                chainBuilder
                    .streamBlocksAndStates(invocation.getArgument(0), invocation.getArgument(1))
                    .map(SignedBlockAndState::getBlock));
  }
}
