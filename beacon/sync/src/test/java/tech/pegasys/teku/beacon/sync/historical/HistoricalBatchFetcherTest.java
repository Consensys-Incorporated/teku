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

package tech.pegasys.teku.beacon.sync.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {DENEB, GLOAS})
public class HistoricalBatchFetcherTest {

  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  private final int maxRequests = 5;

  private Spec spec;
  private ChainBuilder chainBuilder;
  private ChainBuilder forkBuilder;
  private StorageSystem storageSystem;
  private CombinedChainDataClient chainDataClient;
  private List<SignedBeaconBlock> blockBatch;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec);
    storageSystem = InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    storageSystem.chainUpdater().initializeGenesis();

    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    chainBuilder.generateGenesis();
    forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(20);
    forkBuilder.generateBlockAtSlot(2);
    forkBuilder.generateBlocksUpToSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.getFirst();

    chainDataClient =
        new CombinedChainDataClient(
            storageSystem.recentChainData(), mock(StorageQueryChannel.class), spec);

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            Optional.empty(),
            maxRequests);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
  }

  @TestTemplate
  public void run_failsWhenInvalidSignatureFound() {
    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(false));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();
    peer.completePendingRequests();
    assertThat(future).isCompletedExceptionally();
  }

  @TestTemplate
  public void run_returnAllBlocksOnFirstRequest() {
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any(), any(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
  }

  @TestTemplate
  public void run_returnAllBlocksAcrossMultipleRequests() {
    final int limit = (int) Math.ceil(blockBatch.size() / 2.0);
    peer.setBlockRequestFilter(
        allBlocks -> allBlocks.stream().limit(limit).collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any(), any(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
  }

  @TestTemplate
  public void run_requestBatchWithSkippedSlots() {
    final ChainBuilder chain = ChainBuilder.create(spec);
    final int batchSize = 20;
    final SignedBlockAndState genesis = chain.generateGenesis();
    chain.generateBlockAtSlot(5);
    chain.generateBlocksUpToSlot(10);
    chain.generateBlockAtSlot(15);
    chain.generateBlocksUpToSlot(30);

    final SignedBeaconBlock latestBlock = chain.getBlockAtSlot(19);
    final List<SignedBeaconBlock> targetBatch =
        chain
            .streamBlocksAndStates(0, latestBlock.getSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    peer = RespondingEth2Peer.create(spec, chain);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            latestBlock.getSlot(),
            latestBlock.getRoot(),
            UInt64.valueOf(batchSize),
            Optional.empty(),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(genesis.getBlock());

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any(), any(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(targetBatch);
  }

  @TestTemplate
  public void run_peerReturnBlockFromADifferentChain() {
    peer = RespondingEth2Peer.create(spec, forkBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            Optional.empty(),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Received invalid blocks from a different chain");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @TestTemplate
  public void run_throttleExcessivelyAcrossMultipleRequest() {
    final int limit = 1;
    peer.setBlockRequestFilter(
        allBlocks ->
            allBlocks.stream()
                .filter(b -> !b.equals(lastBlockInBatch))
                .limit(limit)
                .collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isPositive();
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @TestTemplate
  public void run_peerReturnsError() {
    final RuntimeException error = new RuntimeException("oops");
    peer.setBlockRequestFilter(
        allBlocks -> {
          throw error;
        });

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCause(error);
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @TestTemplate
  public void run_peerWithholdsFinalBlock() {
    peer.setBlockRequestFilter(
        allBlocks ->
            allBlocks.stream()
                .filter(b -> !b.equals(lastBlockInBatch))
                .collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isPositive();
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @TestTemplate
  public void run_unresponsivePeer() {
    peer.setBlockRequestFilter(allBlocks -> Collections.emptyList());

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isPositive();
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isPositive();
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @TestTemplate
  public void run_peerReturnsInvalidResponsesWithGaps() {
    final int limit = (int) Math.ceil(blockBatch.size() / 2.0);
    peer.setBlockRequestFilter(
        allBlocks -> allBlocks.stream().limit(limit).skip(1).collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    for (int i = 0; i < 2; i++) {
      assertThat(peer.getOutstandingRequests()).isPositive();
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Expected first block to descend from last received block");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }
}
