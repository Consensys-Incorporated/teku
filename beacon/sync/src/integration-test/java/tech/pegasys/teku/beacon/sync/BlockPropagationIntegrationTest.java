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

package tech.pegasys.teku.beacon.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class BlockPropagationIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private final Spec spec = TestSpecFactory.createDefault();
  private final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldFetchUnknownAncestorsOfPropagatedBlock() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    UInt64 currentSlot = SpecConfig.GENESIS_SLOT;

    // Setup node 1
    SyncingNodeManager node1 =
        SyncingNodeManager.create(
            asyncRunner,
            networkFactory,
            validatorKeys,
            c -> c.rpcEncoding(rpcEncoding).gossipEncoding(gossipEncoding));
    node1.getChainUpdater().setCurrentSlot(currentSlot);

    // Add some blocks to node1, which node 2 will need to fetch
    final List<SignedBeaconBlock> blocksToFetch = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      currentSlot = currentSlot.plus(UInt64.ONE);
      final SignedBlockAndState block = node1.getChainBuilder().generateBlockAtSlot(currentSlot);
      node1.getChainUpdater().updateBestBlock(block);
      blocksToFetch.add(block.getBlock());
    }

    // Setup node 2
    SyncingNodeManager node2 =
        SyncingNodeManager.create(
            asyncRunner,
            networkFactory,
            validatorKeys,
            c -> c.rpcEncoding(rpcEncoding).gossipEncoding(gossipEncoding));

    // Connect networks
    Waiter.waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Update slot so that blocks can be imported
    currentSlot = currentSlot.plus(UInt64.ONE);
    node1.setSlot(currentSlot);
    node2.setSlot(currentSlot);

    // Propagate new block
    final SignedBeaconBlock newBlock =
        node1.getChainBuilder().generateBlockAtSlot(currentSlot).getBlock();
    node1.gossipBlock(newBlock);

    // Verify that node2 fetches required blocks in response
    Waiter.waitFor(
        () -> {
          for (SignedBeaconBlock block : blocksToFetch) {
            final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
            assertThatSafeFuture(node2.recentChainData().retrieveBlockByRoot(blockRoot))
                .isCompletedWithNonEmptyOptional();
          }
          // Last block should be imported as well
          final Bytes32 newBlockRoot = newBlock.getMessage().hashTreeRoot();
          assertThatSafeFuture(node2.recentChainData().retrieveBlockByRoot(newBlockRoot))
              .isCompletedWithNonEmptyOptional();
        });
  }
}
