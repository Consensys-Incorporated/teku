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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

class LightClientBootstrapProviderTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final LightClientBootstrapProvider provider =
      new LightClientBootstrapProvider(spec, combinedChainDataClient);

  private SignedBlockAndState blockAndState;

  @BeforeEach
  void setUp() {
    chainBuilder.generateGenesis();
    blockAndState = chainBuilder.generateBlockAtSlot(1);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(UInt64.ZERO));
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(combinedChainDataClient.getBlockByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock())));
    when(combinedChainDataClient.getStateByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getState())));
    when(combinedChainDataClient.isFinalizedEpoch(UInt64.ONE)).thenReturn(true);
    when(combinedChainDataClient.getBlockInEffectAtSlot(spec.computeStartSlotAtEpoch(UInt64.ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock())));
  }

  @Test
  void shouldCreateBootstrapForFinalizedEpochBoundaryBlock() {
    final LightClientBootstrap expected =
        spec.getLightClientUtilRequired(blockAndState.getSlot())
            .createLightClientBootstrap(blockAndState.getState(), blockAndState.getBlock());

    assertThat(getBootstrap(blockAndState.getRoot())).contains(expected);
  }

  @Test
  void shouldReturnEmptyForUnknownBlock() {
    assertThat(getBootstrap(Bytes32.random())).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenStateMissing() {
    when(combinedChainDataClient.getStateByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(getBootstrap(blockAndState.getRoot())).isEmpty();
  }

  @Test
  void shouldReturnEmptyForBlockOlderThanServableRange() {
    final UInt64 currentEpoch =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests()).plus(10);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    assertThat(getBootstrap(blockAndState.getRoot())).isEmpty();
  }

  @Test
  void shouldCreateBootstrapWhenBlockEpochIsBeforeServableRangeButCheckpointEpochIsNot() {
    final UInt64 currentEpoch =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests()).plus(1);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    assertThat(getBootstrap(blockAndState.getRoot())).isPresent();
  }

  @Test
  void shouldReturnEmptyWhenEpochBoundaryNotFinalized() {
    when(combinedChainDataClient.isFinalizedEpoch(UInt64.ONE)).thenReturn(false);

    assertThat(getBootstrap(blockAndState.getRoot())).isEmpty();
    verify(combinedChainDataClient, never()).getStateByBlockRoot(any());
  }

  @Test
  void shouldReturnEmptyWhenBlockIsNotEpochBoundary() {
    final SignedBlockAndState laterBlock = chainBuilder.generateBlockAtSlot(2);
    when(combinedChainDataClient.getBlockInEffectAtSlot(spec.computeStartSlotAtEpoch(UInt64.ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(laterBlock.getBlock())));

    assertThat(getBootstrap(blockAndState.getRoot())).isEmpty();
    verify(combinedChainDataClient, never()).getStateByBlockRoot(any());
  }

  private Optional<LightClientBootstrap> getBootstrap(final Bytes32 blockRoot) {
    return safeJoin(provider.getLightClientBootstrap(blockRoot));
  }
}
