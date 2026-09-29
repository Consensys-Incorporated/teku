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

import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;
import io.libp2p.etc.util.netty.mux.RemoteWriteClosed;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFuture.Interruptor;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.SingleRpcRequestBodySelector;

public class RpcHandler<
        TOutgoingHandler extends RpcRequestHandler,
        TRequest extends RpcRequest,
        TRespHandler extends RpcResponseHandler<?>>
    implements ProtocolBinding<Controller<TOutgoingHandler>> {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration STREAM_INITIALIZE_TIMEOUT = Duration.ofSeconds(5);

  private final AsyncRunner asyncRunner;
  private final RpcMethod<TOutgoingHandler, TRequest, TRespHandler> rpcMethod;
  private final InboundRpcStreamLimiter inboundRpcStreamLimiter;

  final Counter rpcRequestsTotalCounter;
  final Counter rpcRequestsFailedCounter;
  final LabelledMetric<Counter> rpcRequestsSentCounter;

  public RpcHandler(
      final AsyncRunner asyncRunner,
      final RpcMethod<TOutgoingHandler, TRequest, TRespHandler> rpcMethod,
      final MetricsSystem metricsSystem,
      final InboundRpcStreamLimiter inboundRpcStreamLimiter) {
    this.asyncRunner = asyncRunner;
    this.rpcMethod = rpcMethod;
    this.inboundRpcStreamLimiter = inboundRpcStreamLimiter;

    rpcRequestsTotalCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.LIBP2P, "rpc_requests_total", "Total libp2p rpc requests");

    rpcRequestsFailedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.LIBP2P, "rpc_requests_failed", "Failed libp2p rpc requests");

    rpcRequestsSentCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P,
            "rpc_requests_sent",
            "Submitted libp2p rpc requests, including a label for protocolId",
            "protocolId");
  }

  public RpcMethod<TOutgoingHandler, TRequest, TRespHandler> getRpcMethod() {
    return rpcMethod;
  }

  public SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequest(
      final Connection connection, final TRequest request, final TRespHandler responseHandler) {
    return sendRequestWithBodySelector(
        connection, new SingleRpcRequestBodySelector<>(request), responseHandler);
  }

  public SafeFuture<RpcStreamController<TOutgoingHandler>> sendRequestWithBodySelector(
      final Connection connection,
      final RpcRequestBodySelector<TRequest> bodySelector,
      final TRespHandler responseHandler) {
    rpcRequestsTotalCounter.inc();
    final Interruptor closeInterruptor =
        SafeFuture.createInterruptor(connection.closeFuture(), PeerDisconnectedException::new);
    final Interruptor timeoutInterruptor =
        SafeFuture.createInterruptor(
            asyncRunner.getDelayedFuture(STREAM_INITIALIZE_TIMEOUT),
            () ->
                new StreamTimeoutException(
                    "Timed out waiting to initialize stream for protocol(s): "
                        + String.join(",", rpcMethod.getIds())));

    return SafeFuture.notInterrupted(closeInterruptor)
        .thenApply(__ -> connection.muxerSession().createStream(this))
        // waiting for a stream or interrupt
        .thenWaitFor(StreamPromise::getStream)
        .orInterrupt(closeInterruptor, timeoutInterruptor)
        .thenCompose(
            streamPromise -> {
              final SafeFuture<String> protocolIdFuture =
                  SafeFuture.of(streamPromise.getStream().thenCompose(Stream::getProtocol));
              // waiting for controller, writing initial payload or interrupt
              return SafeFuture.of(streamPromise.getController())
                  .<String, RpcStreamController<TOutgoingHandler>>thenCombine(
                      protocolIdFuture,
                      (controller, protocolId) -> {
                        final TOutgoingHandler handler =
                            rpcMethod.createOutgoingRequestHandler(
                                protocolId,
                                requiredRequestBodyForProtocolId(bodySelector, protocolId),
                                responseHandler);
                        controller.setOutgoingRequestHandler(handler);
                        return controller;
                      })
                  .orInterrupt(closeInterruptor, timeoutInterruptor)
                  .thenWaitFor(
                      controller -> {
                        return protocolIdFuture.thenCompose(
                            protocolId -> {
                              final Bytes initialPayload;
                              try {
                                initialPayload =
                                    rpcMethod.encodeRequest(
                                        requiredRequestBodyForProtocolId(bodySelector, protocolId));

                              } catch (Exception e) {
                                return SafeFuture.failedFuture(e);
                              }
                              return controller
                                  .getRpcStream()
                                  .writeBytes(initialPayload)
                                  .thenPeek(
                                      (__) -> rpcRequestsSentCounter.labels(protocolId).inc());
                            });
                      })
                  .orInterrupt(closeInterruptor, timeoutInterruptor)
                  // closing the stream in case of any errors or interruption
                  .whenException(err -> closeStreamAbruptly(streamPromise.getStream().join()));
            })
        .catchAndRethrow(
            err -> {
              rpcRequestsFailedCounter.inc();
              if (ExceptionUtil.hasCause(err, ConnectionClosedException.class)) {
                throw new PeerDisconnectedException(err);
              }
            });
  }

  private TRequest requiredRequestBodyForProtocolId(
      final RpcRequestBodySelector<TRequest> bodySelector, final String protocolId) {
    return bodySelector
        .getBody()
        .apply(protocolId)
        .orElseThrow(
            () -> new IllegalStateException("No request body for protocolId: " + protocolId));
  }

  private void closeStreamAbruptly(final Stream stream) {
    SafeFuture.of(stream.close()).finishDebug(LOG);
  }

  @NotNull
  @Override
  public ProtocolDescriptor getProtocolDescriptor() {
    return new ProtocolDescriptor(rpcMethod.getIds());
  }

  @NotNull
  @Override
  public SafeFuture<Controller<TOutgoingHandler>> initChannel(
      final P2PChannel channel, final String selectedProtocol) {
    final Stream streamChannel = (Stream) channel;
    final Connection connection = streamChannel.getConnection();
    final NodeId nodeId = new LibP2PNodeId(connection.secureSession().getRemoteId());

    final boolean isIncomingStream = !channel.isInitiator();
    if (isIncomingStream && !inboundRpcStreamLimiter.tryAcquire()) {
      LOG.debug(
          "Rejecting inbound RPC stream for protocol {} from {} because the global active stream limit of {} has been reached",
          selectedProtocol,
          nodeId,
          inboundRpcStreamLimiter.getMaximumActiveStreams());
      ignoreFuture(streamChannel.close());
      return SafeFuture.failedFuture(
          new IllegalStateException("Maximum active inbound RPC streams reached"));
    }

    try {
      final Controller<TOutgoingHandler> controller =
          new Controller<>(
              nodeId,
              streamChannel,
              isIncomingStream ? inboundRpcStreamLimiter::release : () -> {});
      if (isIncomingStream) {
        controller.setIncomingRequestHandler(
            rpcMethod.createIncomingRequestHandler(selectedProtocol));
      }
      channel.pushHandler(controller);
      return controller.activeFuture;
    } catch (final Throwable t) {
      if (isIncomingStream) {
        inboundRpcStreamLimiter.release();
      }
      ignoreFuture(streamChannel.close());
      return SafeFuture.failedFuture(t);
    }
  }

  static class Controller<TOutgoingHandler extends RpcRequestHandler>
      extends SimpleChannelInboundHandler<ByteBuf>
      implements RpcStreamController<TOutgoingHandler> {

    private final NodeId nodeId;
    private final Stream p2pStream;
    private final Runnable releaseStreamPermit;
    private Optional<TOutgoingHandler> outgoingRequestHandler = Optional.empty();
    private Optional<RpcRequestHandler> rpcRequestHandler = Optional.empty();
    private RpcStream rpcStream;
    private boolean readCompleted = false;

    protected final SafeFuture<Controller<TOutgoingHandler>> activeFuture = new SafeFuture<>();

    private Controller(
        final NodeId nodeId, final Stream p2pStream, final Runnable releaseStreamPermit) {
      this.nodeId = nodeId;
      this.p2pStream = p2pStream;
      this.releaseStreamPermit = releaseStreamPermit;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
      rpcStream = new LibP2PRpcStream(nodeId, p2pStream, ctx);
      activeFuture.complete(this);
    }

    @Override
    public RpcStream getRpcStream() {
      return rpcStream;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf msg) {
      runHandler(h -> h.processData(nodeId, rpcStream, msg));
    }

    public void setIncomingRequestHandler(final RpcRequestHandler requestHandler) {
      setRequestHandler(requestHandler);
      setFullyActive(requestHandler);
    }

    public void setOutgoingRequestHandler(final TOutgoingHandler requestHandler) {
      setRequestHandler(requestHandler);
      this.outgoingRequestHandler = Optional.of(requestHandler);
      setFullyActive(requestHandler);
    }

    private void setRequestHandler(final RpcRequestHandler rpcRequestHandler) {
      if (this.rpcRequestHandler.isPresent()) {
        throw new IllegalStateException("Attempt to set an already set data handler");
      }
      this.rpcRequestHandler = Optional.of(rpcRequestHandler);
    }

    private void setFullyActive(final RpcRequestHandler rpcRequestHandler) {
      activeFuture.finish(
          () -> {
            rpcRequestHandler.active(nodeId, rpcStream);
          },
          err -> {
            if (err != null) {
              if (Throwables.getRootCause(err) instanceof StreamClosedException) {
                LOG.debug("Stream closed while processing rpc input", err);
              } else {
                LOG.error("Unhandled exception while processing rpc input", err);
              }
            }
          });
    }

    @Override
    public Optional<TOutgoingHandler> getOutgoingRequestHandler() {
      return outgoingRequestHandler;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
      // Completing the future below only records the error, so an out of memory error would be
      // swallowed and leave the node running without usable memory
      ExceptionUtil.escalateIfOutOfMemory(cause);
      LOG.error("Unhandled error while processes req/response", cause);
      final IllegalStateException exception = new IllegalStateException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      closeAbruptly();
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
      onChannelClosed();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (evt instanceof RemoteWriteClosed) {
        onRemoteWriteClosed();
      }
    }

    private void onRemoteWriteClosed() {
      if (!readCompleted) {
        readCompleted = true;
        runHandler(h -> h.readComplete(nodeId, rpcStream));
      }
    }

    private void onChannelClosed() {
      try {
        onRemoteWriteClosed();
        runHandler(h -> h.closed(nodeId, rpcStream));
      } finally {
        rpcRequestHandler = Optional.empty();
        releaseStreamPermit.run();
      }
    }

    private void runHandler(final Consumer<RpcRequestHandler> action) {
      rpcRequestHandler.ifPresentOrElse(action, this::closeAbruptly);
    }

    @VisibleForTesting
    void closeAbruptly() {
      // We're listening for the result of the close future above, so we can ignore this future
      ignoreFuture(p2pStream.close());

      // Make sure to complete activation future in case we are never activated
      activeFuture.completeExceptionally(new StreamClosedException());
    }
  }
}
