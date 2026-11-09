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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.READ_COMPLETE_TIMEOUT;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.RESPONSE_CHUNK_ARRIVAL_TIMEOUT;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ServerErrorException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;

public class Eth2OutgoingRequestHandlerTest
    extends AbstractRequestHandlerTest<
        Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>> {

  private final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRequestRunner = new StubAsyncRunner(timeProvider);
  private final StubAsyncRunner timeoutRunner = new StubAsyncRunner(timeProvider);

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private final AtomicReference<RpcResponseListener<SignedBeaconBlock>> responseListener =
      new AtomicReference<>(RpcResponseListener.from(blocks::add));
  private final Eth2RpcResponseHandler<SignedBeaconBlock, Void> responseHandler =
      Eth2RpcResponseHandler.expectMultipleResponses(res -> responseListener.get().onResponse(res));
  private final int maxChunks = 3;
  private final SafeFuture<Void> finishedProcessingFuture = responseHandler.getCompletedFuture();

  private RpcResponseEncoder<SignedBeaconBlock, ?> responseEncoder;
  private RpcResponseDecoder<SignedBeaconBlock, Bytes> responseDecoder;
  private List<Bytes> chunks;

  @BeforeEach
  @Override
  public void setup() {
    super.setup();
    final SignedBeaconBlockSchema signedBlockSchema =
        phase0Spec
            .forMilestone(SpecMilestone.PHASE0)
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema();
    final RpcContextCodec<Bytes, SignedBeaconBlock> contextCodec =
        RpcContextCodec.noop(signedBlockSchema);
    responseEncoder = new RpcResponseEncoder<>(getRpcEncoding(), contextCodec);
    chunks = IntStream.range(0, maxChunks).mapToObj(this::chunkBytes).collect(Collectors.toList());
  }

  @Override
  protected Eth2OutgoingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createRequestHandler(final BeaconChainMethods beaconChainMethods) {
    final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> method =
        beaconChainMethods.beaconBlocksByRange();
    final BeaconBlocksByRangeRequestMessage request =
        new BeaconBlocksByRangeRequestMessage(UInt64.ZERO, UInt64.valueOf(maxChunks), UInt64.ONE);

    responseDecoder =
        spy(
            RpcResponseDecoder.createContextFreeDecoder(
                getRpcEncoding(), spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema()));
    return new Eth2OutgoingRequestHandler<>(
        asyncRequestRunner,
        timeoutRunner,
        method.getIds().get(0),
        responseDecoder,
        method.shouldReceiveResponse(),
        request,
        responseHandler);
  }

  @Override
  protected RpcEncoding getRpcEncoding() {
    return RpcEncoding.createSszSnappyEncoding(
        TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());
  }

  @Test
  public void processAllExpectedChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
    assertThat(reqHandler.getState()).isIn(State.CLOSED, State.READ_COMPLETE);
    assertThat(blocks.size()).isEqualTo(3);
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void receiveAllChunksThenEncounterErrorWhileProcessing() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
      assertThat(finishedProcessingFuture).isNotDone();
    }
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    assertThat(finishedProcessingFuture).isNotDone();

    // Fail to process the next block
    final RuntimeException error = new RuntimeException("whoops");
    responseListener.set(
        b -> {
          throw error;
        });

    asyncRequestRunner.waitForExactly(1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    verify(rpcStream, atLeastOnce()).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(maxChunks - 1);
    assertThatThrownBy(finishedProcessingFuture::get).hasRootCause(error);
  }

  @Test
  public void processAllValidChunksWhenErrorIsEncountered() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    deliverChunk(0);
    assertThat(finishedProcessingFuture).isNotDone();
    deliverError();
    complete();
    close();

    asyncRequestRunner.waitForExactly(1);
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(1);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
  }

  @Test
  public void sendTooManyChunks() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      if (i == maxChunks - 1) {
        // Send 2 chunks in the last batch of data which should only contain 1 chunk
        final Bytes lastChunk = chunks.get(i);
        final Bytes lastChunkWithExtraChunk = Bytes.concatenate(lastChunk, lastChunk);
        deliverBytes(lastChunkWithExtraChunk);
      } else {
        deliverChunk(i);
      }
    }
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks - 1);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(2);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get)
        .hasRootCause(new ExtraDataAppendedException());
  }

  @Test
  public void sendAllChunksPlusUnexpectedExtraData() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }
    Bytes extraBytes = Bytes.fromHexString("0x112233445566");
    deliverBytes(extraBytes);
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(maxChunks);
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
    assertThatThrownBy(finishedProcessingFuture::get)
        .hasRootCauseInstanceOf(ExtraDataAppendedException.class)
        .hasMessageContaining(extraBytes.toString());
  }

  @Test
  public void shouldWorkWhenSendAllChunksPlusEmptyExtraChunk() throws Exception {
    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }
    deliverBytes(Bytes.EMPTY);
    complete();
    close();

    asyncRequestRunner.waitForExactly(maxChunks);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    verify(rpcStream, never()).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(maxChunks);
    assertThat(finishedProcessingFuture).isCompleted();
    assertThat(reqHandler.getState()).isIn(State.CLOSED, State.READ_COMPLETE);
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void disconnectsIfFirstChunkIsNotReceivedInTime() {
    sendInitialPayload();

    // Run timeouts
    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void timeoutAbortClosesStreamButLeavesDecoderToClosedCallback() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
    verify(responseDecoder, never()).close();

    close();
    verify(responseDecoder).close();

    asyncRequestRunner.executeUntilDone();
    assertThat(finishedProcessingFuture).isCompletedExceptionally();
  }

  @Test
  public void secondClosedCallbackIsHarmless() throws Exception {
    sendInitialPayload();
    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }
    complete();
    close();
    // a second closed callback must be harmless for the decoder
    close();
    verify(responseDecoder, times(2)).close();

    asyncRequestRunner.executeUntilDone();
    timeoutRunner.executeUntilDone();
    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
    assertThat(blocks).hasSize(maxChunks);
  }

  @Test
  public void closedStillTransitionsWhenDecoderCloseThrows() {
    doThrow(new IllegalStateException("close failed")).when(responseDecoder).close();
    sendInitialPayload();
    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }
    complete();

    close();

    assertThat(reqHandler.getState()).isEqualTo(State.CLOSED);
  }

  @Test
  public void doNotDisconnectsIfFirstChunkReceivedInTime() {
    sendInitialPayload();

    deliverChunk(0);

    // Go past the time the first chunk would have timed out but not enough to trigger timeout on
    // the second chunk and ensure the timeout never fires.
    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  public void disconnectsIfSecondChunkNotReceivedInTime() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    deliverChunk(0);
    asyncRequestRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);

    // Run timeouts
    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void abortsWhenNoReadComplete() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    for (int i = 0; i < maxChunks; i++) {
      deliverChunk(i);
    }

    asyncRequestRunner.executeQueuedActions();

    // Run timeouts
    timeProvider.advanceTimeByMillis(READ_COMPLETE_TIMEOUT.toMillis());
    timeoutRunner.executeDueActions();
    verify(rpcStream).closeAbruptly();
  }

  @Test
  public void shouldCompleteExceptionallyWhenClosedWithTruncatedMessage() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    final Bytes chunkBytes = chunks.getFirst();
    deliverBytes(chunkBytes.slice(0, chunkBytes.size() - 1));

    asyncRequestRunner.executeQueuedActions();

    complete();

    assertThat(finishedProcessingFuture).isCompletedExceptionally();
  }

  @Test
  public void doNotDisconnectsIfSecondChunkReceivedInTime() {
    sendInitialPayload();

    timeProvider.advanceTimeByMillis(100);
    deliverChunk(0);
    asyncRequestRunner.executeQueuedActions();
    assertThat(blocks.size()).isEqualTo(1);

    // Second chunk is received just in time
    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    deliverChunk(1);
    asyncRequestRunner.executeQueuedActions();

    // Go past the time the second chunk would have timed out but not enough to trigger timeout on
    // the third chunk and ensure the timeout never fires.
    timeProvider.advanceTimeByMillis(RESPONSE_CHUNK_ARRIVAL_TIMEOUT.toMillis() - 1);
    timeoutRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
    assertThat(blocks.size()).isEqualTo(2);
  }

  @Test
  public void shouldWorkWhenInitialPayloadEventIsLate() throws Exception {
    deliverChunk(0);

    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    for (int i = 1; i < maxChunks; i++) {
      deliverChunk(i);
    }
    complete();
    close();

    assertAllReceivedSuccessfully(maxChunks);
  }

  @Test
  public void shouldWorkWhenInitialPayloadEventAfterReadCompleteWithEmptyResponse()
      throws Exception {
    complete();

    sendInitialPayload();
    verify(rpcStream).closeWriteStream();

    close();

    assertAllReceivedSuccessfully(0);
  }

  private void assertAllReceivedSuccessfully(final int chunkCount) throws InterruptedException {
    asyncRequestRunner.waitForExactly(chunkCount);
    timeoutRunner.executeUntilDone();
    Waiter.waitFor(() -> assertThat(finishedProcessingFuture).isDone());

    assertThat(finishedProcessingFuture).isCompletedWithValue(null);
    assertThat(reqHandler.getState()).isIn(State.CLOSED, State.READ_COMPLETE);
    assertThat(blocks.size()).isEqualTo(chunkCount);
    verify(rpcStream, never()).closeAbruptly();
  }

  private void sendInitialPayload() {
    reqHandler.handleInitialPayloadSent(rpcStream);
  }

  private Bytes chunkBytes(final int chunk) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(chunk);
    return responseEncoder.encodeSuccessfulResponse(block);
  }

  private void deliverError() {
    final Bytes errorChunk = responseEncoder.encodeErrorResponse(new ServerErrorException());
    deliverBytes(errorChunk);
  }

  private void deliverChunk(final int chunk) {
    final Bytes chunkBytes = chunks.get(chunk);
    deliverBytes(chunkBytes);
  }

  private void complete() {
    reqHandler.readComplete(nodeId, rpcStream);
  }

  private void close() {
    reqHandler.closed(nodeId, rpcStream);
  }
}
