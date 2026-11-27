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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;

class RpcResponseCallbackTest {

  private static final String PROTOCOL_ID = "/eth2/beacon_chain/req/test/1/ssz_snappy";

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RpcStream rpcStream = mock(RpcStream.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseEncoder<SszData, ?> responseEncoder = mock(RpcResponseEncoder.class);

  private final SszData data = mock(SszData.class);

  private final SafeFuture<Void> streamClosed = new SafeFuture<>();

  private final RpcResponseCallback<SszData> callback =
      new RpcResponseCallback<>(rpcStream, responseEncoder, asyncRunner, PROTOCOL_ID, streamClosed);

  @Test
  void isClosedShouldReflectStreamClosure() {
    assertThat(callback.isClosed()).isFalse();
    assertThat(callback.getStreamClosedFuture()).isNotDone();

    streamClosed.complete(null);

    assertThat(callback.isClosed()).isTrue();
    assertThat(callback.getStreamClosedFuture()).isCompleted();
  }

  @Test
  void shouldCloseStreamWhenResponseWriteDoesNotComplete() {
    final SafeFuture<Void> writeFuture = new SafeFuture<>();
    when(responseEncoder.encodeSuccessfulResponse(data)).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(writeFuture);
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    final SafeFuture<Void> responseFuture = callback.respond(data);

    assertThat(responseFuture).isNotDone();

    asyncRunner.executeQueuedActions();

    verify(rpcStream).closeAbruptly();
    assertThat(responseFuture).isCompletedExceptionally();
  }

  @Test
  void shouldNotLogErrorWhenWriteFailsWithStopSending() {
    failWriteWith(new ChannelOutputShutdownException("STOP_SENDING frame received"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.respondAndCompleteSuccessfully(data);

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anySatisfy(log -> assertThat(log).contains("STOP_SENDING"));
    }
  }

  @Test
  void shouldNotLogErrorWhenWriteFailsWithClosedChannel() {
    failWriteWith(new ClosedChannelException());

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.respondAndCompleteSuccessfully(data);

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogErrorWhenWriteFailsWithUnexpectedError() {
    failWriteWith(new IllegalStateException("Boom"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class)) {
      callback.respondAndCompleteSuccessfully(data);

      logCaptor.assertErrorLog("Failed to write req/resp response");
    }
  }

  @Test
  void completeWithUnexpectedErrorShouldNotWriteErrorResponseWhenStreamClosedByStopSending() {
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithUnexpectedError(
          new RuntimeException(new ChannelOutputShutdownException("STOP_SENDING frame received")));

      // The peer has stopped reading the stream, so writing a server error response is pointless
      // and would just fail again and resurface as a noisy uncaught exception.
      verify(rpcStream, never()).writeBytes(any());
      verify(rpcStream).closeAbruptly();
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void completeWithUnexpectedErrorShouldNotWriteErrorResponseWhenStreamAlreadyClosed() {
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithUnexpectedError(new StreamClosedException());

      verify(rpcStream, never()).writeBytes(any());
      verify(rpcStream).closeAbruptly();
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void completeWithUnexpectedErrorShouldNotWriteErrorResponseWhenStreamTimedOut() {
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithUnexpectedError(
          new RpcTimeoutException("No response write", Duration.ZERO));

      verify(rpcStream, never()).writeBytes(any());
      verify(rpcStream).closeAbruptly();
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void completeWithUnexpectedErrorShouldWriteErrorResponseForUnexpectedError() {
    when(responseEncoder.encodeErrorResponse(any())).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);

    callback.completeWithUnexpectedError(new IllegalStateException("Boom"));

    verify(rpcStream).writeBytes(any());
  }

  @Test
  void completeWithErrorResponseShouldNotLogErrorWhenWriteFailsWithStopSending() {
    failErrorResponseWriteWith(new ChannelOutputShutdownException("STOP_SENDING frame received"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithErrorResponse(new RpcException.ServerErrorException());

      // The peer sent STOP_SENDING, so failing to write the error response is expected churn, not
      // a noisy uncaught exception.
      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anySatisfy(log -> assertThat(log).contains("STOP_SENDING"));
    }
  }

  @Test
  void completeWithErrorResponseShouldNotLogErrorWhenWriteFailsWithClosedChannel() {
    failErrorResponseWriteWith(new ClosedChannelException());

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithErrorResponse(new RpcException.ServerErrorException());

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void completeWithErrorResponseShouldNotLogErrorWhenWriteFailsWithStreamClosed() {
    failErrorResponseWriteWith(new StreamClosedException());

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.completeWithErrorResponse(new RpcException.ServerErrorException());

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void completeWithErrorResponseShouldLogErrorWhenWriteFailsWithUnexpectedError() {
    failErrorResponseWriteWith(new IllegalStateException("Boom"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class)) {
      callback.completeWithErrorResponse(new RpcException.ServerErrorException());

      logCaptor.assertErrorLog("Failed to write req/resp error response");
    }
  }

  @Test
  void respondAndCompleteSuccessfullyShouldHalfCloseStreamWhenWriteSucceeds() {
    when(responseEncoder.encodeSuccessfulResponse(data)).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);

    callback.respondAndCompleteSuccessfully(data);

    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  void respondAndCompleteSuccessfullyShouldCloseStreamAbruptlyWhenWriteFailsWithStopSending() {
    failWriteWith(new ChannelOutputShutdownException("STOP_SENDING frame received"));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    callback.respondAndCompleteSuccessfully(data);

    // Over QUIC a stream whose peer sent STOP_SENDING can never be half-closed (our FIN is
    // refused), so only an explicit close releases it; otherwise it leaks an inbound RPC stream
    // permit until the connection dies.
    verify(rpcStream).closeAbruptly();
    verify(rpcStream, never()).closeWriteStream();
  }

  @Test
  void respondAndCompleteSuccessfullyShouldCloseStreamAbruptlyWhenWriteFailsWithClosedChannel() {
    failWriteWith(new ClosedChannelException());
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    callback.respondAndCompleteSuccessfully(data);

    verify(rpcStream).closeAbruptly();
  }

  @Test
  void respondAndCompleteSuccessfullyShouldCloseStreamAbruptlyWhenWriteFailsWithUnexpectedError() {
    failWriteWith(new IllegalStateException("Boom"));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class)) {
      callback.respondAndCompleteSuccessfully(data);

      logCaptor.assertErrorLog("Failed to write req/resp response");
    }
    verify(rpcStream).closeAbruptly();
  }

  @Test
  void completeSuccessfullyShouldCloseStreamAbruptlyWhenHalfCloseFails() {
    when(rpcStream.closeWriteStream())
        .thenReturn(
            SafeFuture.failedFuture(
                new ChannelOutputShutdownException("STOP_SENDING frame received")));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    callback.completeSuccessfully();

    verify(rpcStream).closeAbruptly();
  }

  @Test
  void completeSuccessfullyShouldNotCloseStreamAbruptlyWhenHalfCloseSucceeds() {
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);

    callback.completeSuccessfully();

    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  void completeSuccessfullyShouldCloseStreamAbruptlyWhenHalfCloseDoesNotComplete() {
    // our FIN is stuck in the transport
    when(rpcStream.closeWriteStream()).thenReturn(new SafeFuture<>());
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.DEBUG)) {
      callback.completeSuccessfully();

      timeProvider.advanceTimeBy(RpcResponseCallback.RESPONSE_WRITE_TIMEOUT.minusMillis(1));
      asyncRunner.executeDueActions();
      verify(rpcStream, never()).closeAbruptly();

      timeProvider.advanceTimeByMillis(1);
      asyncRunner.executeDueActions();
      verify(rpcStream).closeAbruptly();
      logCaptor.assertDebugLog("Timed out half-closing RPC stream for " + PROTOCOL_ID);
    }
  }

  @Test
  void completeSuccessfullyShouldNotCloseStreamAbruptlyWhenHalfCloseCompletesInTime() {
    final SafeFuture<Void> halfCloseFuture = new SafeFuture<>();
    when(rpcStream.closeWriteStream()).thenReturn(halfCloseFuture);

    callback.completeSuccessfully();
    timeProvider.advanceTimeBy(RpcResponseCallback.RESPONSE_WRITE_TIMEOUT.minusMillis(1));
    asyncRunner.executeDueActions();
    halfCloseFuture.complete(null);

    timeProvider.advanceTimeBy(RpcResponseCallback.RESPONSE_WRITE_TIMEOUT);
    asyncRunner.executeDueActions();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  void completeWithErrorResponseShouldHalfCloseStreamWhenWriteSucceeds() {
    when(responseEncoder.encodeErrorResponse(any())).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);

    callback.completeWithErrorResponse(new RpcException.ServerErrorException());

    verify(rpcStream).closeWriteStream();
    verify(rpcStream, never()).closeAbruptly();
  }

  @Test
  void completeWithErrorResponseShouldCloseStreamAbruptlyWhenWriteFailsWithStopSending() {
    failErrorResponseWriteWith(new ChannelOutputShutdownException("STOP_SENDING frame received"));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    callback.completeWithErrorResponse(new RpcException.ServerErrorException());

    verify(rpcStream).closeAbruptly();
    verify(rpcStream, never()).closeWriteStream();
  }

  @Test
  void completeWithErrorResponseShouldCloseStreamAbruptlyWhenWriteFailsWithUnexpectedError() {
    failErrorResponseWriteWith(new IllegalStateException("Boom"));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);

    callback.completeWithErrorResponse(new RpcException.ServerErrorException());

    verify(rpcStream).closeAbruptly();
  }

  private void failErrorResponseWriteWith(final Throwable error) {
    when(responseEncoder.encodeErrorResponse(any())).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.failedFuture(error));
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);
  }

  private void failWriteWith(final Throwable error) {
    when(responseEncoder.encodeSuccessfulResponse(any())).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.failedFuture(error));
    when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);
  }
}
