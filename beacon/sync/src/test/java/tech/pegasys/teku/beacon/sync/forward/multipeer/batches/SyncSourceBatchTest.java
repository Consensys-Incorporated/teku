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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SERVER_ERROR_CODE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.StubSyncSource;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;

public class SyncSourceBatchTest {

  private final UInt64 gloasForkEpoch = UInt64.valueOf(1000);
  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          specConfigBuilder -> {
            specConfigBuilder
                .denebForkEpoch(UInt64.ZERO)
                .electraForkEpoch(gloasForkEpoch.minus(2))
                .fuluForkEpoch(gloasForkEpoch.minus(1))
                .gloasForkEpoch(gloasForkEpoch);
          });
  private final long gloasSlot = spec.computeStartSlotAtEpoch(gloasForkEpoch).plus(70).longValue();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final TargetChain targetChain =
      chainWith(new SlotAndBlockRoot(UInt64.valueOf(1000), Bytes32.ZERO));
  private final InlineEventThread eventThread = new InlineEventThread();
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final ConflictResolutionStrategy conflictResolutionStrategy =
      mock(ConflictResolutionStrategy.class);
  private final Map<Batch, List<StubSyncSource>> syncSources = new HashMap<>();

  @BeforeEach
  public void setUp() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
  }

  @Test
  void requestMoreBlocks_shouldRequestFromStartOnFirstRequest() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(75));
    getSyncSource(batch).assertRequestedBlocks(70, 50);
    verify(callback).run();
    verifyNoMoreInteractions(callback);
  }

  @Test
  void requestMoreBlocks_shouldRequestFromSlotAfterLastBlockOnSubsequentRequests() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(75));
    getSyncSource(batch).assertRequestedBlocks(70, 50);
    verify(callback).run();
    verifyNoMoreInteractions(callback);

    batch.requestMoreBlocks(callback);
    getSyncSource(batch).assertRequestedBlocks(76, 44);
  }

  @Test
  void requestMoreBlocks_shouldResetAndSelectNewPeerAfterDisconnection() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);

    // First request returns some data, so the batch isn't in initial state
    final StubSyncSource firstSyncSource = getSyncSource(batch);
    firstSyncSource.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(72));
    verify(callback).run();
    batch.markFirstBlockConfirmed();
    batch.markAsContested();

    // Second request should go to the same source
    batch.requestMoreBlocks(callback);
    firstSyncSource.assertRequestedBlocks(73, 47);

    assertThatBatch(batch).isNotEmpty();

    // But this requests fails
    firstSyncSource.failRequest(new PeerDisconnectedException());
    // The request is complete, so should call the callback
    verify(callback, times(2)).run();

    // And the batch should be back in initial state
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotContested();
    assertThatBatch(batch).hasUnconfirmedFirstBlock();

    // Third request selects a new peer to request data from
    batch.requestMoreBlocks(callback);
    assertThat(syncSources.get(batch)).hasSize(2);
    final StubSyncSource secondSyncSource = getSyncSource(batch);
    assertThat(secondSyncSource).isNotSameAs(firstSyncSource);
    secondSyncSource.assertRequestedBlocks(70, 50);
  }

  @Test
  void requestMoreBlocks_shouldRequestBlobSidecarsWhenRequired() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);

    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    // only receiving last block (70 + 50 - 1)
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(119);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    receiveBlocks(batch, block);
    receiveBlobSidecars(batch, blobSidecars);

    getSyncSource(batch).assertRequestedBlocks(70, 50);
    getSyncSource(batch).assertRequestedBlobSidecars(70, 50);

    verify(callback).run();
    verifyNoMoreInteractions(callback);

    assertThat(batch.isComplete()).isTrue();
    assertThat(batch.getBlocks()).containsExactly(block);
    assertThat(batch.getBlobSidecarsByBlockRoot())
        .hasSize(1)
        .containsEntry(block.getRoot(), blobSidecars);
  }

  @Test
  void markContested_shouldVerifyBatchWithConflictResolutionStrategy() {
    final Batch batch = createBatch(1, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    batch.markAsContested();

    verify(conflictResolutionStrategy).verifyBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldBeInvalidWhenInconsistentResponseReceived() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(callback);

    requestError(
        batch,
        new BlocksByRangeResponseInvalidResponseException(
            InvalidResponseType.BLOCK_PARENT_ROOT_DOES_NOT_MATCH));

    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
    verify(callback).run();
    // Invalid blocks are discarded
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotComplete();
  }

  @Test
  void shouldBeInvalidWhenMalformedResponseReceived() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(callback);

    requestError(batch, new DeserializationFailedException());

    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
    verify(callback).run();
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotComplete();
  }

  @Test
  void shouldNotBeInvalidWhenPeerRespondsWithError() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(callback);

    requestError(batch, new RpcException(SERVER_ERROR_CODE, "peer failed"));

    verify(conflictResolutionStrategy, never()).reportInvalidBatch(any(), any());
    verify(callback).run();
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotComplete();
  }

  @Test
  void shouldReportAsInvalidToConflictResolutionStrategyWhenMarkedAsInvalid() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markAsInvalid();

    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldNotReportAsInvalidToConflictResolutionStrategyWhenAlreadyContested() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markAsContested();

    verify(conflictResolutionStrategy).verifyBatch(batch, getSyncSource(batch));
    // Conflict resolution determines the batch is invalid
    batch.markAsInvalid();
    // But it shouldn't be notified again
    verifyNoMoreInteractions(conflictResolutionStrategy);
  }

  @Test
  void shouldReportAsInvalidWhenSecondRequestDoesNotFormChainWithExistingBlocks() {
    final Batch batch = createBatch(10, 10);

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(10));
    verifyNoInteractions(conflictResolutionStrategy);

    // Second request returns a block whose parent doesn't match the previous block
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(11));

    // Node is disagreeing with itself so mark it as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldReportAsInvalidWhenUnexpectedNumberOfBlobSidecarsWereReceived() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    final Batch batch = createBatch(10, 10);

    batch.requestMoreBlocks(() -> {});

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(19);

    final List<BlobSidecar> blobSidecars =
        new ArrayList<>(dataStructureUtil.randomBlobSidecarsForBlock(block));
    // receiving more sidecars than expected
    blobSidecars.add(
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .build());

    receiveBlocks(batch, block);
    receiveBlobSidecars(batch, blobSidecars);

    // batch should be reported as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldReportAsInvalidWhenBlobSidecarsWithUnexpectedSlotsWereReceived() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    final Batch batch = createBatch(10, 10);

    batch.requestMoreBlocks(() -> {});

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(19);

    final int numberOfKzgCommitments =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody()).getBlobKzgCommitments().size();
    // receiving sidecars with different slot than the block
    final SignedBeaconBlock blockWrong = dataStructureUtil.randomSignedBeaconBlock(20);
    final List<BlobSidecar> blobSidecars =
        IntStream.range(0, numberOfKzgCommitments)
            .mapToObj(
                index ->
                    dataStructureUtil
                        .createRandomBlobSidecarBuilder()
                        .signedBeaconBlockHeader(blockWrong.asHeader())
                        .index(UInt64.valueOf(index))
                        .build())
            .collect(toList());

    receiveBlocks(batch, block);
    receiveBlobSidecars(batch, blobSidecars);

    // batch should be reported as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldMarkBatchAsInconsistentWhenUnexpectedBlobSidecarsWithRootsWereReceived() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    final Batch batch = createBatch(10, 10);

    batch.requestMoreBlocks(() -> {});

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(19);

    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobSidecar> unexpectedBlobSidecars = new ArrayList<>(blobSidecars);
    // receiving sidecars with unknown roots
    unexpectedBlobSidecars.addAll(
        dataStructureUtil.randomBlobSidecarsForBlock(
            dataStructureUtil.randomSignedBeaconBlock(18)));

    receiveBlocks(batch, block);
    receiveBlobSidecars(batch, unexpectedBlobSidecars);

    assertThat(batch.isComplete()).isTrue();
    assertThat(batch.getBlocks()).containsExactly(block);
    assertThat(batch.getBlobSidecarsByBlockRoot())
        .hasSize(1)
        .containsEntry(block.getRoot(), blobSidecars);

    // reputation of the peer should have been adjusted
    verify(conflictResolutionStrategy).reportInconsistentBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldSkipMakingRequestWhenNoTargetPeerIsAvailable() {
    final SyncSourceSelector emptySourceSelector = Optional::empty;
    final SyncSourceBatch batch =
        new SyncSourceBatch(
            eventThread,
            spec,
            blobSidecarManager,
            emptySourceSelector,
            conflictResolutionStrategy,
            targetChain,
            UInt64.ONE,
            UInt64.ONE);

    final Runnable callback = mock(Runnable.class);
    batch.requestMoreBlocks(callback);
    // Should only invoke callback via executeLater to give a chance for disconnected events to
    // be processed on the event thread, rather than executing a tight loop retrying requests
    // to disconnected peers.
    verifyNoInteractions(callback);
    eventThread.executePendingTasks();
    verify(callback).run();
    assertThatBatch(batch).isNotAwaitingBlocks();
  }

  @Test
  void requestMoreBlocks_shouldRequestExecutionPayloadsForGloas() {
    final int batchCount = 5;

    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(gloasSlot, batchCount);

    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    generateRealisticChain(gloasSlot, batch.getLastSlot().longValue());

    receiveBlocksFromChainBuilder(batch, gloasSlot, batch.getLastSlot().longValue());
    receiveExecutionPayloadsFromChainBuilder(
        batch, gloasSlot, batch.getLastSlot().longValue(), __ -> true);

    getSyncSource(batch).assertRequestedBlocks(gloasSlot, batchCount);
    getSyncSource(batch).assertRequestedExecutionPayloadEnvelopes(gloasSlot, batchCount);

    verify(callback).run();
    verifyNoMoreInteractions(callback);

    assertThat(batch.isComplete()).isTrue();
    assertThat(batch.getBlocks())
        .hasSize(batchCount)
        .containsExactlyElementsOf(
            chainBuilder
                .streamBlocksAndStates(gloasSlot, batch.getLastSlot().longValue())
                .map(SignedBlockAndState::getBlock)
                .toList());
    final Map<Bytes32, SignedExecutionPayloadEnvelope> expectedExecutionPayloadsByBlockRoot =
        chainBuilder
            .streamExecutionPayloads(gloasSlot, batch.getLastSlot().longValue())
            .collect(
                Collectors.toMap(
                    SignedExecutionPayloadEnvelope::getBeaconBlockRoot, Function.identity()));
    assertThat(batch.getExecutionPayloadsByBlockRoot())
        .hasSize(batchCount)
        .isEqualTo(expectedExecutionPayloadsByBlockRoot);
  }

  @Test
  void shouldReportBatchAsInvalidWhenExecutionPayloadIsMissing() {
    final int batchCount = 5;

    final Batch batch = createBatch(gloasSlot, batchCount);

    batch.requestMoreBlocks(() -> {});

    generateRealisticChain(gloasSlot, batch.getLastSlot().longValue());

    receiveBlocksFromChainBuilder(batch, gloasSlot, batch.getLastSlot().longValue());
    receiveExecutionPayloadsFromChainBuilder(
        batch,
        gloasSlot,
        batch.getLastSlot().longValue(),
        // skip one execution payload in the middle of the batch to make it invalid
        executionPayload ->
            !executionPayload.getSlot().equals(UInt64.valueOf(gloasSlot + (batchCount / 2))));

    // batch should be reported as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldReportBatchAsInvalidWhenExecutionPayloadIsMissingFromAPreviousRequest() {
    final int batchCount = 5;

    final Batch batch = createBatch(gloasSlot, batchCount);

    generateRealisticChain(gloasSlot, batch.getLastSlot().longValue());

    // first request
    batch.requestMoreBlocks(() -> {});

    final long lastSlotOfFirstRequest = batch.getLastSlot().longValue() - 1;

    // receive blocks and execution payloads apart from the last one
    receiveBlocksFromChainBuilder(batch, gloasSlot, lastSlotOfFirstRequest);
    receiveExecutionPayloadsFromChainBuilder(
        batch,
        gloasSlot,
        lastSlotOfFirstRequest,
        // skip the last execution payload to make the next request invalid
        executionPayload ->
            !executionPayload.getSlot().equals(UInt64.valueOf(lastSlotOfFirstRequest)));

    verifyNoInteractions(conflictResolutionStrategy);

    // second request returns the last block whose parent doesn't have an execution payload
    batch.requestMoreBlocks(() -> {});

    receiveBlocks(batch, chainBuilder.getBlockAtSlot(batch.getLastSlot().longValue()));
    receiveExecutionPayloads(
        batch, chainBuilder.getExecutionPayloadAtSlot(batch.getLastSlot()).orElse(null));

    // batch should be reported as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  protected void generateRealisticChain(final long fromSlot, final long toSlot) {
    chainBuilder.generateGenesis();
    for (long i = fromSlot; i <= toSlot; i++) {
      chainBuilder.generateBlockAtSlot(i);
    }
  }

  protected Batch createBatch(final long startSlot, final long count) {
    final List<StubSyncSource> syncSources = new ArrayList<>();
    final SyncSourceSelector syncSourceProvider =
        () -> {
          final StubSyncSource source = new StubSyncSource();
          syncSources.add(source);
          return Optional.of(source);
        };
    final SyncSourceBatch batch =
        new SyncSourceBatch(
            eventThread,
            spec,
            blobSidecarManager,
            syncSourceProvider,
            conflictResolutionStrategy,
            targetChain,
            UInt64.valueOf(startSlot),
            UInt64.valueOf(count));
    this.syncSources.put(batch, syncSources);
    return batch;
  }

  protected void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks) {
    getSyncSource(batch).receiveBlocks(blocks);
  }

  protected void receiveBlobSidecars(final Batch batch, final List<BlobSidecar> blobSidecars) {
    getSyncSource(batch).receiveBlobSidecars(blobSidecars.toArray(new BlobSidecar[] {}));
  }

  protected void receiveExecutionPayloads(
      final Batch batch, final SignedExecutionPayloadEnvelope... executionPayloads) {
    getSyncSource(batch).receiveExecutionPayloadEnvelopes(executionPayloads);
  }

  protected void receiveBlocksFromChainBuilder(
      final Batch batch, final long fromSlot, final long toSlot) {
    getSyncSource(batch)
        .receiveBlocks(
            chainBuilder
                .streamBlocksAndStates(fromSlot, toSlot)
                .map(SignedBlockAndState::getBlock)
                .toArray(SignedBeaconBlock[]::new));
  }

  protected void receiveExecutionPayloadsFromChainBuilder(
      final Batch batch,
      final long fromSlot,
      final long toSlot,
      final Predicate<SignedExecutionPayloadEnvelope> executionPayloadsFilter) {
    getSyncSource(batch)
        .receiveExecutionPayloadEnvelopes(
            chainBuilder
                .streamExecutionPayloads(fromSlot, toSlot)
                .filter(executionPayloadsFilter)
                .toArray(SignedExecutionPayloadEnvelope[]::new));
  }

  protected void requestError(final Batch batch, final Throwable error) {
    getSyncSource(batch).failRequest(error);
  }

  /** Get the most recent sync source for a batch. */
  private StubSyncSource getSyncSource(final Batch batch) {
    final List<StubSyncSource> syncSources = this.syncSources.get(batch);
    return syncSources.getLast();
  }

  @Test
  void getFirstSlot_shouldReturnFirstSlot() {
    final long firstSlot = 5;
    final Batch batch = createBatch(firstSlot, 20);
    assertThatBatch(batch).hasFirstSlot(firstSlot);
  }

  @Test
  void getLastSlot_shouldCalculateLastSlot() {
    final Batch batch = createBatch(5, 20);
    // Slot 5 is the first block returned so it's one less slot than you might expect
    assertThatBatch(batch).hasLastSlot(UInt64.valueOf(24));
  }

  @Test
  void isComplete_shouldNotBeCompleteOnCreation() {
    assertThatBatch(createBatch(10, 15)).isNotComplete();
  }

  @Test
  void isComplete_shouldBeCompleteAfterMarkComplete() {
    final Batch batch = createBatch(10, 15);
    batch.markComplete();
    assertThatBatch(batch).isComplete();
  }

  @Test
  void isComplete_shouldBeCompleteAfterInitialRequestReturnsNoBlocks() {
    final Batch batch = createBatch(10, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThatBatch(batch).isComplete();
    assertThatBatch(batch).isEmpty();
  }

  @Test
  void isComplete_shouldBeCompleteAfterInitialRequestReturnsBlockInLastSlot() {
    final Batch batch = createBatch(10, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(batch.getLastSlot()));
    assertThatBatch(batch).isComplete();
    assertThatBatch(batch).isNotEmpty();
  }

  @Test
  void getFirstBlock_shouldBeEmptyInitially() {
    assertThat(createBatch(10, 1).getFirstBlock()).isEmpty();
  }

  @Test
  void getFirstBlock_shouldBeEmptyAfterEmptyResponse() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThat(batch.getFirstBlock()).isEmpty();
  }

  @Test
  void getFirstBlock_shouldContainFirstReturnedBlock() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    receiveBlocks(batch, firstBlock, dataStructureUtil.randomSignedBeaconBlock(11));
    assertThat(batch.getFirstBlock()).contains(firstBlock);
  }

  @Test
  void getFirstBlock_shouldBeFirstBlockAfterMultipleRequests() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final SignedBeaconBlock secondBlock =
        dataStructureUtil.randomSignedBeaconBlock(11, firstBlock.getRoot());
    receiveBlocks(batch, firstBlock, secondBlock);

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(12, secondBlock.getRoot()));
    assertThat(batch.getFirstBlock()).contains(firstBlock);
  }

  @Test
  void getLastBlock_shouldBeEmptyInitially() {
    assertThat(createBatch(10, 1).getLastBlock()).isEmpty();
  }

  @Test
  void getLastBlock_shouldBeEmptyAfterEmptyResponse() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThat(batch.getLastBlock()).isEmpty();
  }

  @Test
  void getLastBlock_shouldContainLastReturnedBlock() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final SignedBeaconBlock lastBlock = dataStructureUtil.randomSignedBeaconBlock(11);
    receiveBlocks(batch, firstBlock, lastBlock);
    assertThat(batch.getLastBlock()).contains(lastBlock);
  }

  @Test
  void getLastBlock_shouldBeLastBlockAfterMultipleRequests() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final SignedBeaconBlock secondBlock =
        dataStructureUtil.randomSignedBeaconBlock(11, firstBlock.getRoot());
    receiveBlocks(batch, firstBlock, secondBlock);

    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock lastBlock =
        dataStructureUtil.randomSignedBeaconBlock(12, secondBlock.getRoot());
    receiveBlocks(batch, lastBlock);
    assertThat(batch.getLastBlock()).contains(lastBlock);
  }

  @Test
  void getBlocks_shouldEmptyListInitially() {
    assertThat(createBatch(5, 6).getBlocks()).isEmpty();
  }

  @Test
  void getBlocks_shouldReturnAllBlocksFromMultipleRequests() {
    final Batch batch = createBatch(0, 60);
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2, block1.getRoot());
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3, block2.getRoot());
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(4, block3.getRoot());
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, block1, block2);
    assertThat(batch.getBlocks()).containsExactly(block1, block2);

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, block3, block4);
    assertThat(batch.getBlocks()).containsExactly(block1, block2, block3, block4);
  }

  @Test
  void isConfirmed_shouldOnlyBeConfirmedOnceFirstAndLastBlocksAreConfirmed() {
    final Batch batch = createBatch(75, 22);
    assertThatBatch(batch).isNotConfirmed();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).isNotConfirmed();

    batch.markLastBlockConfirmed();
    assertThatBatch(batch).isConfirmed();
  }

  @Test
  void isConfirmed_shouldNotBeConfirmedWhenOnlyLastBlockIsConfirmed() {
    final Batch batch = createBatch(75, 22);
    assertThatBatch(batch).isNotConfirmed();

    batch.markLastBlockConfirmed();
    assertThatBatch(batch).isNotConfirmed();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).isConfirmed();
  }

  @Test
  void markFirstBlockConfirmed_shouldNotifyConflictResolutionStrategyWhenFirstConfirmed() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markLastBlockConfirmed();
    verifyNoMoreInteractions(conflictResolutionStrategy);

    batch.markFirstBlockConfirmed();
    verify(conflictResolutionStrategy).reportConfirmedBatch(batch, batch.getSource().orElseThrow());

    // Only notifies the first time
    batch.markFirstBlockConfirmed();
    verifyNoMoreInteractions(conflictResolutionStrategy);
  }

  @Test
  void markLastBlockConfirmed_shouldNotifyConflictResolutionStrategyWhenFirstConfirmed() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markFirstBlockConfirmed();
    verifyNoMoreInteractions(conflictResolutionStrategy);

    batch.markLastBlockConfirmed();
    verify(conflictResolutionStrategy).reportConfirmedBatch(batch, batch.getSource().orElseThrow());

    // Only notifies the first time
    batch.markLastBlockConfirmed();
    verifyNoMoreInteractions(conflictResolutionStrategy);
  }

  @Test
  void isFirstBlockConfirmed_shouldBeTrueOnlyAfterBeingMarked() {
    final Batch batch = createBatch(1, 3);
    assertThatBatch(batch).hasUnconfirmedFirstBlock();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).hasConfirmedFirstBlock();
  }

  @Test
  void isContested_shouldBeContestedWhenMarkedContested() {
    final Batch batch = createBatch(5, 10);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThatBatch(batch).isNotContested();

    batch.markAsContested();
    assertThatBatch(batch).isContested();
  }

  @Test
  void isAwaitingBlocks_shouldBeFalseInitially() {
    final Batch batch = createBatch(5, 2);
    assertThatBatch(batch).isNotAwaitingBlocks();
    batch.requestMoreBlocks(() -> {});

    assertThatBatch(batch).isAwaitingBlocks();
  }

  @Test
  void isAwaitingBlocks_shouldBeAwaitingBlocksWhenRequestIsPending() {
    final Batch batch = createBatch(5, 2);
    batch.requestMoreBlocks(() -> {});

    assertThatBatch(batch).isAwaitingBlocks();
  }

  @Test
  void isAwaitingBlocks_shouldNotBeAwaitingBlocksWhenRequestIsCompleted() {
    final Batch batch = createBatch(5, 2);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);

    assertThatBatch(batch).isNotAwaitingBlocks();
  }

  @Test
  void isAwaitingBlocks_shouldNotBeAwaitingBlocksWhenRequestFails() {
    final Batch batch = createBatch(5, 2);
    batch.requestMoreBlocks(() -> {});
    requestError(batch, new RuntimeException("Oops"));

    assertThatBatch(batch).isNotAwaitingBlocks();
  }

  @Test
  void requestMoreBlocks_shouldInvokeCallbackWhenRequestComplete() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(5, 2);
    batch.requestMoreBlocks(callback);
    receiveBlocks(batch);

    verify(callback).run();
  }

  @Test
  void requestMoreBlocks_shouldInvokeCallbackWhenRequestFails() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(5, 2);
    batch.requestMoreBlocks(callback);
    requestError(batch, new RuntimeException("Oops"));

    verify(callback).run();
  }

  @Test
  void requestMoreBlocks_shouldThrowErrorWhenRequestingBlocksForCompleteBatch() {
    final Batch batch = createBatch(5, 8);
    batch.markComplete();
    assertThatThrownBy(() -> batch.requestMoreBlocks(() -> {}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void markInvalid_shouldDiscardCurrentStateAndRerequestData() {
    final Batch batch = createBatch(5, 10);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(
        batch,
        dataStructureUtil.randomSignedBeaconBlock(6),
        dataStructureUtil.randomSignedBeaconBlock(14));
    assertThatBatch(batch).isComplete();

    batch.markAsInvalid();

    // Discards the current blocks and is no longer complete
    assertThatBatch(batch).isNotComplete();
    assertThatBatch(batch).isEmpty();

    final SignedBeaconBlock realBlock = dataStructureUtil.randomSignedBeaconBlock(12);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, realBlock);
    assertThat(batch.getBlocks()).containsExactly(realBlock);
  }

  @Test
  void markInvalid_shouldNotThrowWhenSourceIsAbsent() {
    final Batch batch = createBatch(0, 1);
    syncSources.put(batch, new ArrayList<>());
    batch.markAsInvalid();
  }

  @Test
  void markContested_shouldNotThrowWhenSourceIsAbsent() {
    final Batch batch = createBatch(0, 1);
    syncSources.put(batch, new ArrayList<>());
    batch.markAsContested();
  }
}
