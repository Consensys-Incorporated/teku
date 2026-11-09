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

package tech.pegasys.teku.api.blockselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlockSelectorFactoryTest {
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final SpecMilestone milestone = spec.getGenesisSpec().getMilestone();
  private final SignedBeaconBlock block = data.randomSignedBeaconBlock(100);

  private final BlockSelectorFactory blockSelectorFactory = new BlockSelectorFactory(spec, client);

  @Test
  public void headSelector_shouldGetBestBlock() throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    List<BlockAndMetaData> blockList = blockSelectorFactory.headSelector().getBlocks().get();
    verify(client).getChainHead();
    assertThat(blockList).containsExactly(withMetaData(blockAndState.getBlock()));
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlock()
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedBlock()).thenReturn(Optional.of(block));
    List<BlockAndMetaData> blockList = blockSelectorFactory.finalizedSelector().getBlocks().get();
    verify(client).getFinalizedBlock();
    assertThat(blockList).containsExactly(withMetaData(block));
  }

  @Test
  public void genesisSelector_shouldGetSlotZero() throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<BlockAndMetaData> blockList = blockSelectorFactory.genesisSelector().getBlocks().get();
    verify(client).getBlockAtSlotExact(UInt64.ZERO);
    assertThat(blockList).containsExactly(withMetaData(block));
  }

  @Test
  public void blockRootSelector_shouldGetBlockByBlockRoot()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState head =
        data.randomSignedBlockAndState(block.getSlot().plus(3), block.getRoot());
    final ChainHead chainHead = ChainHead.create(head);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.isCanonicalBlock(block.getSlot(), block.getRoot(), chainHead.getRoot()))
        .thenReturn(true);
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<BlockAndMetaData> blockList =
        blockSelectorFactory.blockRootSelector(block.getRoot()).getBlocks().get();
    verify(client).getBlockByBlockRoot(block.getRoot());
    assertThat(blockList).containsExactly(withMetaData(block));
  }

  @Test
  public void slotSelector_shouldGetBlockAtSlotExact()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState head = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(head)));
    when(client.getBlockAtSlotExact(block.getSlot(), head.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<BlockAndMetaData> blockList =
        blockSelectorFactory.slotSelector(block.getSlot()).getBlocks().get();
    verify(client).getBlockAtSlotExact(block.getSlot(), head.getRoot());
    assertThat(blockList).containsExactly(withMetaData(block));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> blockSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> blockSelectorFactory.stateRootSelector(data.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> blockSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, blockSelectorFactory::justifiedSelector);
  }

  private BlockAndMetaData withMetaData(final SignedBeaconBlock block) {
    return new BlockAndMetaData(block, milestone, false, true, client.isFinalized(block.getSlot()));
  }
}
