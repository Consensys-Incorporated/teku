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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.RootCauseExceptionHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ServerErrorException;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;

class RpcResponseCallback<TResponse extends SszData> implements ResponseCallback<TResponse> {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting static final Duration RESPONSE_WRITE_TIMEOUT = Duration.ofSeconds(10);

  private final RpcResponseEncoder<TResponse, ?> responseEncoder;
  private final RpcStream rpcStream;
  private final AsyncRunner asyncRunner;
  private final String protocolId;

  public RpcResponseCallback(
      final RpcStream rpcStream,
      final RpcResponseEncoder<TResponse, ?> responseEncoder,
      final AsyncRunner asyncRunner,
      final String protocolId) {
    this.rpcStream = rpcStream;
    this.responseEncoder = responseEncoder;
    this.asyncRunner = asyncRunner;
    this.protocolId = protocolId;
  }

  @Override
  public SafeFuture<Void> respond(final TResponse data) {
    return writeWithTimeout(responseEncoder.encodeSuccessfulResponse(data));
  }

  @Override
  public void respondAndCompleteSuccessfully(final TResponse data) {
    respond(data)
        .thenRun(this::closeWriteStream)
        .finish(
            error -> {
              logWriteFailure("response", error);
              closeStreamAbruptly();
            });
  }

  @Override
  public void completeSuccessfully() {
    closeWriteStream();
  }

  @Override
  public void completeWithErrorResponse(final RpcException error) {
    LOG.debug("Responding to RPC request with error: {}", error.getErrorMessageString());
    writeWithTimeout(responseEncoder.encodeErrorResponse(error))
        .finish(
            this::closeWriteStream,
            writeError -> {
              logWriteFailure("error response", writeError);
              closeStreamAbruptly();
            });
  }

  /** Half-closes the stream after a complete response, closing it fully if that is not possible. */
  private void closeWriteStream() {
    rpcStream
        .closeWriteStream()
        .finish(
            error -> {
              LOG.trace("Failed to half-close stream after response, closing it", error);
              closeStreamAbruptly();
            });
  }

  /**
   * A stream whose response could not be written is never half-closed: over QUIC a peer that sent
   * STOP_SENDING refuses our FIN, so the stream would stay open (and keep its inbound RPC stream
   * permit) until the connection dies. Only an explicit close releases it.
   */
  private void closeStreamAbruptly() {
    rpcStream.closeAbruptly().finishTrace(LOG);
  }

  private void logWriteFailure(final String what, final Throwable error) {
    RootCauseExceptionHandler.builder()
        .addCatch(
            StreamClosedException.class,
            err -> LOG.trace("Failed to write {} because stream was closed", what, err))
        .addCatch(
            StreamTimeoutException.class,
            err -> LOG.trace("Failed to write {} because response write timed out", what, err))
        .addCatch(
            ClosedChannelException.class,
            err -> LOG.trace("Failed to write {} because channel was closed", what, err))
        .addCatch(
            ChannelOutputShutdownException.class,
            err ->
                LOG.trace(
                    "Failed to write {} because peer stopped reading the stream (STOP_SENDING)",
                    what,
                    err))
        .defaultCatch(err -> LOG.error("Failed to write req/resp {}", what, err))
        .accept(error);
  }

  private SafeFuture<Void> writeWithTimeout(final Bytes responseBytes) {
    final SafeFuture<Void> writeFuture;
    try {
      writeFuture = rpcStream.writeBytes(responseBytes);
    } catch (final Throwable t) {
      return SafeFuture.failedFuture(t);
    }
    final SafeFuture.Interruptor timeoutInterruptor =
        SafeFuture.createInterruptor(
            asyncRunner.getDelayedFuture(RESPONSE_WRITE_TIMEOUT),
            () ->
                new RpcTimeoutException(
                    "Timed out waiting for response write for protocol " + protocolId,
                    RESPONSE_WRITE_TIMEOUT));
    return writeFuture
        .orInterrupt(timeoutInterruptor)
        .catchAndRethrow(
            error -> {
              if (Throwables.getRootCause(error) instanceof RpcTimeoutException) {
                LOG.debug("Timed out writing RPC response for {}. Closing stream.", protocolId);
                rpcStream.closeAbruptly().finishDebug(LOG);
              }
            });
  }

  @Override
  public void completeWithUnexpectedError(final Throwable error) {
    if (error instanceof PeerDisconnectedException) {
      LOG.trace("Not sending RPC response as peer has already disconnected");
      // But close the stream just to be completely sure we don't leak any resources.
      rpcStream.closeAbruptly().finishTrace(LOG);
      return;
    }
    // A closed/half-closed stream is expected peer churn. Writing a server error response to it is
    // pointless: the write would just fail again and resurface as a noisy uncaught exception (e.g.
    // the peer sent STOP_SENDING, which is a ChannelOutputShutdownException rather than a
    // ClosedChannelException, so each close type must be matched separately).
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof StreamClosedException
        || rootCause instanceof ClosedChannelException
        || rootCause instanceof ChannelOutputShutdownException
        || rootCause instanceof StreamTimeoutException) {
      LOG.trace("Not sending RPC response as the stream is already closed or timed out", error);
      rpcStream.closeAbruptly().finishTrace(LOG);
      return;
    }
    completeWithErrorResponse(new ServerErrorException());
  }
}
