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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import com.google.common.base.MoreObjects;
import io.libp2p.core.Stream;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;

public class LibP2PRpcStream implements RpcStream {

  private final Stream p2pStream;
  private final ChannelHandlerContext ctx;
  private final AtomicBoolean writeStreamClosed = new AtomicBoolean(false);
  private final NodeId nodeId;
  private final Runnable onWriteStreamClosed;
  private volatile SafeFuture<Void> halfCloseFuture;

  public LibP2PRpcStream(
      final NodeId nodeId,
      final Stream p2pStream,
      final ChannelHandlerContext ctx,
      final Runnable onWriteStreamClosed) {
    this.nodeId = nodeId;
    this.p2pStream = p2pStream;
    this.ctx = ctx;
    this.onWriteStreamClosed = onWriteStreamClosed;
  }

  @Override
  public SafeFuture<Void> writeBytes(final Bytes bytes) throws StreamClosedException {
    if (writeStreamClosed.get()) {
      throw new StreamClosedException();
    }
    final ByteBuf reqByteBuf = ctx.alloc().buffer();
    reqByteBuf.writeBytes(bytes.toArrayUnsafe());

    return toSafeFuture(ctx.writeAndFlush(reqByteBuf));
  }

  @Override
  public SafeFuture<Void> closeAbruptly() {
    writeStreamClosed.set(true);
    return SafeFuture.of(p2pStream.close()).thenApply((res) -> null);
  }

  @Override
  public SafeFuture<Void> closeWriteStream() {
    writeStreamClosed.set(true);
    final SafeFuture<Void> future =
        SafeFuture.of(p2pStream.closeWrite()).thenRun(onWriteStreamClosed);
    halfCloseFuture = future;
    return future;
  }

  boolean isHalfCloseRequested() {
    return halfCloseFuture != null;
  }

  boolean isHalfCloseCompleted() {
    final SafeFuture<Void> future = halfCloseFuture;
    return future != null && future.isDone();
  }

  private SafeFuture<Void> toSafeFuture(final ChannelFuture channelFuture) {
    final SafeFuture<Void> future = new SafeFuture<>();
    channelFuture.addListener(
        (f) -> {
          if (f.isSuccess()) {
            future.complete(null);
          } else {
            future.completeExceptionally(f.cause());
          }
        });
    return future;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeId", nodeId)
        .add("channel id", ctx.channel().id())
        .add("writeStreamClosed", writeStreamClosed)
        .toString();
  }
}
