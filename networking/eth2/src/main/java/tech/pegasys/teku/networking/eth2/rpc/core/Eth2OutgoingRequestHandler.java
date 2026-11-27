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

import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.ABORTED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.CLOSED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.DATA_COMPLETED;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.EXPECT_DATA;
import static tech.pegasys.teku.networking.eth2.rpc.core.Eth2OutgoingRequestHandler.State.READ_COMPLETE;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public class Eth2OutgoingRequestHandler<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    implements RpcRequestHandler {

  @VisibleForTesting
  enum State {
    EXPECT_DATA,
    DATA_COMPLETED,
    READ_COMPLETE,
    CLOSED,
    ABORTED
  }

  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting static final Duration READ_COMPLETE_TIMEOUT = Duration.ofSeconds(10);
  @VisibleForTesting static final Duration RESPONSE_CHUNK_ARRIVAL_TIMEOUT = Duration.ofSeconds(10);

  private final AsyncRunner asyncRunner;
  private final int maximumResponseChunks;
  private final Eth2RpcResponseHandler<TResponse, ?> responseHandler;

  private final AsyncRunner timeoutRunner;
  private final AtomicInteger currentChunkCount = new AtomicInteger(0);
  private final AtomicReference<State> state;
  private final AtomicReference<AsyncResponseProcessor<TResponse>> responseProcessor =
      new AtomicReference<>();

  private final String protocolId;
  private final RpcResponseDecoder<TResponse, ?> responseDecoder;
  private final boolean shouldReceiveResponse;

  public Eth2OutgoingRequestHandler(
      final AsyncRunner asyncRunner,
      final AsyncRunner timeoutRunner,
      final String protocolId,
      final RpcResponseDecoder<TResponse, ?> responseDecoder,
      final boolean shouldReceiveResponse,
      final TRequest request,
      final Eth2RpcResponseHandler<TResponse, ?> responseHandler) {
    this.asyncRunner = asyncRunner;
    this.timeoutRunner = timeoutRunner;
    this.maximumResponseChunks = request.getMaximumResponseChunks();
    this.responseHandler = responseHandler;
    this.responseDecoder = responseDecoder;
    this.shouldReceiveResponse = shouldReceiveResponse;
    this.protocolId = protocolId;
    this.state = new AtomicReference<>(shouldReceiveResponse ? EXPECT_DATA : DATA_COMPLETED);
  }

  public void handleInitialPayloadSent(final RpcStream stream) {
    // Close the write side of the stream
    stream.closeWriteStream().finishStackTrace();
    if (shouldReceiveResponse) {
      // Setup initial chunk timeout
      ensureNextResponseChunkArrivesInTime(stream, currentChunkCount.get(), currentChunkCount);
    } else {
      ensureReadCompleteArrivesInTime(stream);
    }
  }

  @Override
  public void active(final NodeId nodeId, final RpcStream rpcStream) {}

  @Override
  public void processData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf data) {
    if (!data.isReadable()) {
      return;
    }

    try {
      if (state.get() != EXPECT_DATA) {
        throw new RpcException.ExtraDataAppendedException(" extra data: " + bufToString(data));
      }

      final List<TResponse> maybeResponses = responseDecoder.decodeNextResponses(data);
      final int chunksReceived = currentChunkCount.addAndGet(maybeResponses.size());

      if (chunksReceived > maximumResponseChunks) {
        throw new ExtraDataAppendedException();
      }

      for (TResponse maybeResponse : maybeResponses) {
        getResponseProcessor(rpcStream).processResponse(maybeResponse);
      }
      if (chunksReceived < maximumResponseChunks) {
        if (!maybeResponses.isEmpty()) {
          ensureNextResponseChunkArrivesInTime(rpcStream, chunksReceived, currentChunkCount);
        }
      } else {
        if (!transferToState(DATA_COMPLETED, List.of(EXPECT_DATA))) {
          abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
          return;
        }
        ensureReadCompleteArrivesInTime(rpcStream);
      }
    } catch (final RpcException e) {
      abortRequest(rpcStream, e);
    } catch (final Throwable t) {
      LOG.error("Encountered error while processing response", t);
      abortRequest(rpcStream, t);
    }
  }

  private AsyncResponseProcessor<TResponse> getResponseProcessor(final RpcStream rpcStream) {
    return responseProcessor.updateAndGet(
        oldVal ->
            Objects.requireNonNullElseGet(
                oldVal,
                () ->
                    new AsyncResponseProcessor<>(
                        asyncRunner,
                        responseHandler,
                        throwable -> abortRequest(rpcStream, throwable))));
  }

  private String bufToString(final ByteBuf buf) {
    final int contentSize = Integer.min(buf.readableBytes(), 1024);
    String bufContent = "";
    if (contentSize > 0) {
      final ByteBuf bufSlice = buf.slice(0, contentSize);
      final byte[] bytes = new byte[bufSlice.readableBytes()];
      bufSlice.getBytes(0, bytes);
      bufContent += Bytes.wrap(bytes);
      if (contentSize < buf.readableBytes()) {
        bufContent += "...";
      }
    }
    return "ByteBuf{" + buf + ", content: " + bufContent + "}";
  }

  @Override
  public void readComplete(final NodeId nodeId, final RpcStream rpcStream) {
    if (!transferToState(READ_COMPLETE, List.of(DATA_COMPLETED, EXPECT_DATA))) {
      abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
      return;
    }

    try {
      responseDecoder.complete();
      completeRequest(rpcStream);
    } catch (RpcException e) {
      abortRequest(rpcStream, e);
    }
  }

  @Override
  public void closed(final NodeId nodeId, final RpcStream rpcStream) {
    // see RpcRequestHandler for the threading contract
    try {
      responseDecoder.close();
    } catch (final Exception ex) {
      LOG.debug("Encountered error while closing response decoder", ex);
    }
    if (!transferToState(CLOSED, List.of(READ_COMPLETE))) {
      abortRequest(rpcStream, new IllegalStateException("Unexpected state: " + state));
    }
  }

  public SafeFuture<Void> getCompletedFuture() {
    return responseHandler.getCompletedFuture();
  }

  private boolean transferToState(final State toState, final Collection<State> fromStates) {
    for (State fromState : fromStates) {
      if (state.compareAndSet(fromState, toState)) {
        return true;
      }
    }
    return false;
  }

  private void completeRequest(final RpcStream rpcStream) {
    getResponseProcessor(rpcStream)
        .finishProcessing()
        .thenAccept(
            (__) -> {
              try {
                responseHandler.onCompleted();
                LOG.trace("Complete request");
              } catch (final Throwable t) {
                LOG.error("Encountered error while completing outgoing request", t);
                responseHandler.onCompleted(t);
              }
            })
        .exceptionally(
            (err) -> {
              abortRequest(rpcStream, err, true);
              return null;
            })
        .finishStackTrace();
  }

  private void abortRequest(final RpcStream rpcStream, final Throwable error) {
    abortRequest(rpcStream, error, false);
  }

  private void abortRequest(final RpcStream rpcStream, final Throwable error, final boolean force) {
    if (!transferToState(ABORTED, List.of(EXPECT_DATA, DATA_COMPLETED, READ_COMPLETE)) && !force) {
      return;
    }

    LOG.trace("Abort request: {}", error.getMessage());

    // the decoder is released in closed(), see RpcRequestHandler
    try {
      rpcStream.closeAbruptly().finishDebug(LOG);
    } catch (Exception ex) {
      LOG.debug("Encountered error while aborting outgoing request", ex);
    } finally {
      getResponseProcessor(rpcStream)
          .finishProcessing()
          .always(() -> responseHandler.onCompleted(error));
    }
  }

  private void ensureNextResponseChunkArrivesInTime(
      final RpcStream stream,
      final int previousResponseCount,
      final AtomicInteger currentResponseCount) {
    timeoutRunner
        .runAfterDelay(
            () -> {
              if (previousResponseCount == currentResponseCount.get()) {
                abortRequest(
                    stream,
                    new RpcTimeoutException(
                        "Timed out waiting for response chunk " + previousResponseCount,
                        RESPONSE_CHUNK_ARRIVAL_TIMEOUT));
              }
            },
            RESPONSE_CHUNK_ARRIVAL_TIMEOUT)
        .finishStackTrace();
  }

  private void ensureReadCompleteArrivesInTime(final RpcStream stream) {
    timeoutRunner
        .runAfterDelay(
            () -> {
              if (!(state.get() == READ_COMPLETE || state.get() == CLOSED)) {
                abortRequest(
                    stream,
                    new RpcTimeoutException(
                        "Timed out waiting for read channel close", READ_COMPLETE_TIMEOUT));
              }
            },
            READ_COMPLETE_TIMEOUT)
        .finishStackTrace();
  }

  @VisibleForTesting
  State getState() {
    return state.get();
  }

  @Override
  public String toString() {
    return "Eth2OutgoingRequestHandler{" + protocolId + '}';
  }
}
