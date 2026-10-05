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

package tech.pegasys.teku.networking.p2p.libp2p;

import com.google.common.annotations.VisibleForTesting;
import io.libp2p.core.ChannelVisitor;
import io.libp2p.core.Connection;
import io.libp2p.etc.util.netty.mux.MuxId;
import io.libp2p.mux.mplex.MplexFlag;
import io.libp2p.mux.mplex.MplexFrame;
import io.libp2p.mux.yamux.YamuxFlag;
import io.libp2p.mux.yamux.YamuxFrame;
import io.libp2p.mux.yamux.YamuxType;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.async.FutureUtil;

/**
 * Adds additional upfront verification of connections.
 *
 * <p>Supports both {@link YamuxFrame} and {@link MplexFrame}
 */
public class MuxFirewall implements ChannelVisitor<Connection> {

  private static final Logger LOG = LogManager.getLogger();
  private static final long ONE_SECOND = 1000;

  private final int remoteOpenStreamsRateLimit;
  private final int remoteParallelOpenStreamsLimit;
  private final Supplier<Long> currentTimeSupplier;

  public MuxFirewall(
      final int remoteOpenStreamsRateLimit, final int remoteParallelOpenStreamsLimit) {
    this(remoteOpenStreamsRateLimit, remoteParallelOpenStreamsLimit, System::currentTimeMillis);
  }

  @VisibleForTesting
  MuxFirewall(
      final int remoteOpenStreamsRateLimit,
      final int remoteParallelOpenStreamsLimit,
      final Supplier<Long> currentTimeSupplier) {
    this.remoteOpenStreamsRateLimit = remoteOpenStreamsRateLimit;
    this.remoteParallelOpenStreamsLimit = remoteParallelOpenStreamsLimit;
    this.currentTimeSupplier = currentTimeSupplier;
  }

  private void remoteParallelOpenStreamLimitExceeded(final MuxFirewallHandler peerMplexHandler) {
    LOG.debug("Abruptly closing peer connection due to exceeding parallel open streams limit");
    FutureUtil.ignoreFuture(peerMplexHandler.getConnection().close());
  }

  private void remoteOpenFrameRateLimitExceeded(final MuxFirewallHandler peerMplexHandler) {
    LOG.debug("Abruptly closing peer connection due to exceeding open mux frame rate limit");
    FutureUtil.ignoreFuture(peerMplexHandler.getConnection().close());
  }

  @Override
  public void visit(@NotNull final Connection connection) {
    final MuxFirewallHandler firewallHandler = new MuxFirewallHandler(connection);
    connection.pushHandler(firewallHandler);
  }

  private class MuxFirewallHandler extends ChannelDuplexHandler {

    private final Connection connection;
    private int openFrameCounter = 0;
    private long startCounterTime = 0;
    private final Set<MuxId> remoteOpenedStreamIds = new HashSet<>();

    public MuxFirewallHandler(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      final MuxFrame muxFrame = resolveMuxFrame(msg);
      boolean blockFrame = false;
      if (muxFrame.indicatesStreamOpening()) {
        remoteOpenedStreamIds.add(muxFrame.getId());
        if (remoteOpenedStreamIds.size() > remoteParallelOpenStreamsLimit) {
          remoteParallelOpenStreamLimitExceeded(this);
          blockFrame = true;
        }

        final long curTime = currentTimeSupplier.get();
        if (curTime - startCounterTime > ONE_SECOND) {
          startCounterTime = curTime;
          openFrameCounter = 0;
        } else {
          openFrameCounter++;
          if (openFrameCounter > remoteOpenStreamsRateLimit) {
            remoteOpenFrameRateLimitExceeded(this);
            blockFrame = true;
          }
        }
      } else if (muxFrame.indicatesStreamClosing() || muxFrame.indicatesStreamResetting()) {
        remoteOpenedStreamIds.remove(muxFrame.getId());
      }
      if (!blockFrame) {
        ctx.fireChannelRead(msg);
      }
    }

    @Override
    public void write(
        final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
      final MuxFrame muxFrame = resolveMuxFrame(msg);
      if (muxFrame.indicatesStreamResetting()) {
        // Track only RESET since CLOSE from local doesn't close the stream for writing from remote
        remoteOpenedStreamIds.remove(muxFrame.getId());
      }
      // ignoring since write() just returns `promise` instance
      FutureUtil.ignoreFuture(ctx.write(msg, promise));
    }

    public Connection getConnection() {
      return connection;
    }
  }

  private MuxFrame resolveMuxFrame(final Object msg) {
    if (msg instanceof MplexFrame mplexFrame) {
      return new MuxFrame() {

        private final MplexFlag mplexFlag = mplexFrame.getFlag();

        @Override
        public MuxId getId() {
          return mplexFrame.getId();
        }

        @Override
        public boolean indicatesStreamOpening() {
          return mplexFlag.getType() == MplexFlag.Type.OPEN;
        }

        @Override
        public boolean indicatesStreamClosing() {
          return mplexFlag.getType() == MplexFlag.Type.CLOSE;
        }

        @Override
        public boolean indicatesStreamResetting() {
          return mplexFlag.getType() == MplexFlag.Type.RESET;
        }
      };
    }
    if (msg instanceof YamuxFrame yamuxFrame) {
      return new MuxFrame() {

        private final Set<YamuxFlag> yamuxFlags = yamuxFrame.getFlags();

        @Override
        public MuxId getId() {
          return yamuxFrame.getId();
        }

        @Override
        public boolean indicatesStreamOpening() {
          boolean isDataOrWindowUpdate =
              yamuxFrame.getType() == YamuxType.DATA
                  || yamuxFrame.getType() == YamuxType.WINDOW_UPDATE;
          return isDataOrWindowUpdate
              && (yamuxFlags.contains(YamuxFlag.SYN) || yamuxFlags.contains(YamuxFlag.ACK));
        }

        @Override
        public boolean indicatesStreamClosing() {
          return yamuxFlags.contains(YamuxFlag.FIN);
        }

        @Override
        public boolean indicatesStreamResetting() {
          return yamuxFlags.contains(YamuxFlag.RST);
        }
      };
    }
    throw new IllegalArgumentException("Unsupported type of mux frame: " + msg.getClass());
  }

  private interface MuxFrame {
    MuxId getId();

    boolean indicatesStreamOpening();

    boolean indicatesStreamClosing();

    boolean indicatesStreamResetting();
  }
}
