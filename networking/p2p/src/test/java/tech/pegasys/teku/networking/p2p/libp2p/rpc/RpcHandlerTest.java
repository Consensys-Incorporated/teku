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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxer.Session;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.VersionBasedRpcRequestBodySelector;

@SuppressWarnings("unchecked")
public class RpcHandlerTest {

  private static final PeerId REMOTE_PEER_ID =
      PeerId.fromBase58("16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5");

  StubAsyncRunner asyncRunner = new StubAsyncRunner();
  RpcMethod<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> rpcMethod = mock(RpcMethod.class);
  StubMetricsSystem metricsSystem = new StubMetricsSystem();
  RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> rpcHandler =
      new RpcHandler<>(asyncRunner, rpcMethod, metricsSystem, new InboundRpcStreamLimiter(128));

  Connection connection = mock(Connection.class);
  Session session = mock(Session.class);
  StreamPromise<Controller<RpcRequestHandler>> streamPromise =
      new StreamPromise<>(new CompletableFuture<>(), new CompletableFuture<>());
  CompletableFuture<Unit> closeFuture = new CompletableFuture<>();
  CompletableFuture<String> protocolIdFuture = new CompletableFuture<>();
  SafeFuture<Void> writeFuture = new SafeFuture<>();

  Stream stream = mock(Stream.class);
  Controller<RpcRequestHandler> controller = mock(Controller.class);
  RpcStream rpcStream = mock(RpcStream.class);
  final RpcResponseHandler<?> responseHandler = mock(RpcResponseHandler.class);
  final RpcRequest request = mock(RpcRequest.class);
  final String protocolId = "test";

  @BeforeEach
  void init() {
    when(connection.muxerSession()).thenReturn(session);
    when(session.createStream((ProtocolBinding<Controller<RpcRequestHandler>>) any()))
        .thenReturn(streamPromise);
    when(connection.closeFuture()).thenReturn(closeFuture);

    when(controller.getRpcStream()).thenReturn(rpcStream);
    when(rpcStream.writeBytes(any())).thenReturn(writeFuture);
    when(stream.getProtocol()).thenReturn(protocolIdFuture);
  }

  @Test
  void shouldRejectInboundStreamsAtLimitAndReleasePermitWhenStreamCloses() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(1);
    final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> handler =
        createRpcHandler(limiter);
    final RpcRequestHandler requestHandler = mock(RpcRequestHandler.class);
    when(rpcMethod.createIncomingRequestHandler(protocolId)).thenReturn(requestHandler);

    final Stream firstStream = createP2PStream(false);
    final AtomicReference<Controller<RpcRequestHandler>> firstController =
        captureController(firstStream);
    final SafeFuture<Controller<RpcRequestHandler>> firstInit =
        handler.initChannel(firstStream, protocolId);
    final ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    firstController.get().channelActive(context);

    assertThat(firstInit).isCompletedWithValue(firstController.get());
    assertThat(limiter.getActiveStreams()).isOne();

    final Stream rejectedStream = createP2PStream(false);
    final SafeFuture<Controller<RpcRequestHandler>> rejectedInit =
        handler.initChannel(rejectedStream, protocolId);

    assertThatSafeFuture(rejectedInit).isCompletedExceptionallyWith(IllegalStateException.class);
    verify(rejectedStream).close();
    assertThat(limiter.getActiveStreams()).isOne();

    firstController.get().handlerRemoved(context);

    assertThat(limiter.getActiveStreams()).isZero();

    final Stream replacementStream = createP2PStream(false);
    final AtomicReference<Controller<RpcRequestHandler>> replacementController =
        captureController(replacementStream);
    final SafeFuture<Controller<RpcRequestHandler>> replacementInit =
        handler.initChannel(replacementStream, protocolId);
    replacementController.get().channelActive(context);

    assertThat(replacementInit).isCompletedWithValue(replacementController.get());
    assertThat(limiter.getActiveStreams()).isOne();
    verify(replacementStream, never()).close();

    replacementController.get().handlerRemoved(context);
    assertThat(limiter.getActiveStreams()).isZero();
  }

  @Test
  void shouldReleaseInboundPermitWhenHandlerInitializationFails() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(1);
    final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> handler =
        createRpcHandler(limiter);
    final Stream inboundStream = createP2PStream(false);
    when(rpcMethod.createIncomingRequestHandler(protocolId))
        .thenThrow(new IllegalArgumentException("Invalid protocol"));

    final SafeFuture<Controller<RpcRequestHandler>> initFuture =
        handler.initChannel(inboundStream, protocolId);

    assertThatSafeFuture(initFuture).isCompletedExceptionallyWith(IllegalArgumentException.class);
    assertThat(limiter.getActiveStreams()).isZero();
    verify(inboundStream).close();
  }

  @Test
  void shouldNotApplyInboundLimitToOutboundStreams() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(1);
    assertThat(limiter.tryAcquire()).isTrue();
    final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> handler =
        createRpcHandler(limiter);
    final Stream outboundStream = createP2PStream(true);
    final AtomicReference<Controller<RpcRequestHandler>> outboundController =
        captureController(outboundStream);
    final ChannelHandlerContext context = mock(ChannelHandlerContext.class);

    final SafeFuture<Controller<RpcRequestHandler>> initFuture =
        handler.initChannel(outboundStream, protocolId);
    outboundController.get().channelActive(context);

    assertThat(initFuture).isCompletedWithValue(outboundController.get());
    assertThat(limiter.getActiveStreams()).isOne();
    verify(outboundStream, never()).close();
    verify(rpcMethod, never()).createIncomingRequestHandler(any());

    outboundController.get().handlerRemoved(context);

    assertThat(limiter.getActiveStreams()).isOne();

    limiter.release();
    assertThat(limiter.getActiveStreams()).isZero();
  }

  @Test
  void sendRequest_positiveCase() {
    final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    stream.getProtocol().complete(protocolId);

    assertThat(future).isNotDone();
    writeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    // interrupting after completion shouldn't affect anything
    closeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 1);
    assertRequestsFailedCounterValue(0);
  }

  @Test
  void sendRequestMissingRequestBodyMappingShouldFail() {
    final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, null, responseHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    stream.getProtocol().complete(protocolId);

    assertThat(future).isCompletedExceptionally();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @Test
  void sendRequestWithBodySelector_positiveCase() {
    final VersionBasedRpcRequestBodySelector<RpcRequest> bodySelector =
        new VersionBasedRpcRequestBodySelector<>(Map.of(protocolId, request));

    final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequestWithBodySelector(connection, bodySelector, responseHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    stream.getProtocol().complete(protocolId);

    assertThat(future).isNotDone();
    writeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    // interrupting after completion shouldn't affect anything
    closeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 1);
    assertRequestsFailedCounterValue(0);
  }

  @Test
  void sendRequestWithBodySelectorMissingRequestBodyMappingShouldFail() {
    final VersionBasedRpcRequestBodySelector<RpcRequest> emptyBodySelector =
        new VersionBasedRpcRequestBodySelector<>(Map.of());
    final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequestWithBodySelector(connection, emptyBodySelector, responseHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    stream.getProtocol().complete(protocolId);

    assertThat(future).isCompletedExceptionally();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @Test
  void sendRequest_streamClosedRightBeforeCreateStreamShouldThrowPeerDisconnectedException() {

    when(session.createStream((ProtocolBinding<Controller<RpcRequestHandler>>) any()))
        .thenThrow(new ConnectionClosedException());
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThatSafeFuture(future).isCompletedExceptionallyWith(PeerDisconnectedException.class);

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @Test
  void sendRequest_noStreamCreationWhenInitiallyClosed() {
    closeFuture.complete(null);

    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    verify(connection, never()).muxerSession();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeStreamResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeControllerResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeProtocolIdResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    streamPromise.getController().complete(controller);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeControllerResolvesButAfterProtocolIdResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    stream.getProtocol().complete(protocolId);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeInitialPayloadWritten(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    streamPromise.getController().complete(controller);
    stream.getProtocol().complete(protocolId);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();

    assertRequestsTotalCounterValue(1);
    assertRequestsSentCounterValue(protocolId, 0);
    assertRequestsFailedCounterValue(1);
  }

  @SuppressWarnings("UnnecessaryAsync")
  private Class<? extends Exception> executeInterrupts(
      final boolean closeStream, final boolean exceedTimeout) {
    final AtomicReference<Class<? extends Exception>> expectedException =
        new AtomicReference<>(null);
    if (closeStream) {
      closeFuture.complete(null);
      expectedException.set(PeerDisconnectedException.class);
    }
    if (exceedTimeout) {
      asyncRunner.executeQueuedActions();
      expectedException.compareAndSet(null, StreamTimeoutException.class);
    }

    return expectedException.get();
  }

  public static java.util.stream.Stream<Arguments> getInterruptParams() {
    return java.util.stream.Stream.of(
        Arguments.of(true, false), Arguments.of(false, true), Arguments.of(true, true));
  }

  private void assertRequestsSentCounterValue(final String protocolId, final long expectedValue) {
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.LIBP2P, "rpc_requests_sent", protocolId))
        .isEqualTo(expectedValue);
  }

  private void assertRequestsTotalCounterValue(final long expectedValue) {
    assertThat(metricsSystem.getCounterValue(TekuMetricCategory.LIBP2P, "rpc_requests_total"))
        .isEqualTo(expectedValue);
  }

  private void assertRequestsFailedCounterValue(final long expectedValue) {
    assertThat(metricsSystem.getCounterValue(TekuMetricCategory.LIBP2P, "rpc_requests_failed"))
        .isEqualTo(expectedValue);
  }

  @Test
  void shouldEscalateOutOfMemoryErrorFromTheChannelInsteadOfOnlyRecordingIt() {
    // Netty throws OutOfDirectMemoryError when the direct memory limit is reached, and
    // -XX:+ExitOnOutOfMemoryError does not detect it because it is thrown by Java code. Without
    // escalation this handler would only log it and complete the future, so the node would keep
    // running with no usable direct memory.
    final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> handler =
        createRpcHandler(new InboundRpcStreamLimiter(1));
    when(rpcMethod.createIncomingRequestHandler(protocolId))
        .thenReturn(mock(RpcRequestHandler.class));
    final Stream p2pStream = createP2PStream(false);
    final AtomicReference<Controller<RpcRequestHandler>> capturedController =
        captureController(p2pStream);
    final SafeFuture<Controller<RpcRequestHandler>> init =
        handler.initChannel(p2pStream, protocolId);
    final ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    capturedController.get().channelActive(context);
    assertThat(init).isCompleted();

    final OutOfMemoryError error =
        new OutOfMemoryError("Cannot reserve 1048576 bytes of direct buffer memory");
    final List<Throwable> escalated = new ArrayList<>();
    final Thread thread = Thread.currentThread();
    final UncaughtExceptionHandler original = thread.getUncaughtExceptionHandler();
    thread.setUncaughtExceptionHandler((t, e) -> escalated.add(e));
    try {
      capturedController.get().exceptionCaught(context, error);
    } finally {
      thread.setUncaughtExceptionHandler(original);
    }

    assertThat(escalated).containsExactly(error);
  }

  private RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<?>> createRpcHandler(
      final InboundRpcStreamLimiter limiter) {
    return new RpcHandler<>(asyncRunner, rpcMethod, new StubMetricsSystem(), limiter);
  }

  private Stream createP2PStream(final boolean initiator) {
    final Stream p2pStream = mock(Stream.class);
    final io.libp2p.core.security.SecureChannel.Session secureSession =
        mock(io.libp2p.core.security.SecureChannel.Session.class);
    when(p2pStream.isInitiator()).thenReturn(initiator);
    when(p2pStream.getConnection()).thenReturn(connection);
    when(p2pStream.close()).thenReturn(CompletableFuture.completedFuture(Unit.INSTANCE));
    when(connection.secureSession()).thenReturn(secureSession);
    when(secureSession.getRemoteId()).thenReturn(REMOTE_PEER_ID);
    return p2pStream;
  }

  private AtomicReference<Controller<RpcRequestHandler>> captureController(final Stream p2pStream) {
    final AtomicReference<Controller<RpcRequestHandler>> capturedController =
        new AtomicReference<>();
    doAnswer(
            invocation -> {
              capturedController.set(invocation.getArgument(0));
              return null;
            })
        .when(p2pStream)
        .pushHandler(any(ChannelHandler.class));
    return capturedController;
  }
}
