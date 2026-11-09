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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.libp2p.core.Connection;
import io.libp2p.mux.mplex.MplexFlag;
import io.libp2p.mux.mplex.MplexFrame;
import io.libp2p.mux.mplex.MplexId;
import io.libp2p.mux.yamux.YamuxFlag;
import io.libp2p.mux.yamux.YamuxFrame;
import io.libp2p.mux.yamux.YamuxId;
import io.libp2p.mux.yamux.YamuxType;
import io.libp2p.transport.implementation.ConnectionOverNetty;
import io.libp2p.transport.implementation.NettyTransport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class MuxFirewallTest {
  private static final ChannelId DUMMY_CHANNEL_ID = DefaultChannelId.newInstance();

  private final AtomicLong time = new AtomicLong();
  private final MuxFirewall firewall = new MuxFirewall(10, 20, time::get);
  private final ByteBuf data1K = Unpooled.buffer().writeBytes(new byte[1024]);
  private final EmbeddedChannel channel = new EmbeddedChannel();
  private final Connection connectionOverNetty =
      new ConnectionOverNetty(channel, mock(NettyTransport.class), true);
  private final List<String> passedMessages = new ArrayList<>();

  @BeforeEach
  void init() {
    firewall.visit(connectionOverNetty);
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                passedMessages.add(msg.toString());
              }
            });
  }

  private enum MuxType {
    MPLEX,
    YAMUX
  }

  private void writeOneInbound(final Object message) {
    try {
      boolean res = channel.writeOneInbound(message).await(1000L);
      assertThat(res).isTrue();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDisconnectsOnRateLimitExceed(final MuxType muxType) {
    for (int i = 0; i < 20; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      writeOneInbound(createCloseReceiverFrame(muxType, i));
      time.incrementAndGet();
    }
    assertThat(channel.isOpen()).isFalse();
    // hasSizeBetween: giving the Firewall some flexibility to pass 1 extra OPEN frame before
    // closing connection
    assertThat(passedMessages).hasSizeBetween(20, 22).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDoesNotDisconnectOnAllowedRate(final MuxType muxType) {
    for (int i = 0; i < 500; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      writeOneInbound(createCloseReceiverFrame(muxType, i));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(1000).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDisconnectsOnExceededAfterAllowedRate(final MuxType muxType) {
    for (int i = 0; i < 100; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      writeOneInbound(createCloseReceiverFrame(muxType, i));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200).doesNotHaveDuplicates();

    for (int i = 100; i < 200; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      writeOneInbound(createCloseReceiverFrame(muxType, i));
      time.addAndGet(80); // ~12 per sec
    }
    assertThat(channel.isOpen()).isFalse();
    // 220 + 2: giving the Firewall some flexibility to pass 1 extra OPEN frame before closing
    // connection
    assertThat(passedMessages).hasSizeLessThan(220 + 2).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDoesntDisconnectOnHighDataRate(final MuxType muxType) {
    for (int i = 0; i < 500; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      for (int j = 0; j < 30; j++) {
        writeOneInbound(createDataFrame(muxType, i));
      }
      writeOneInbound(createCloseReceiverFrame(muxType, i));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(500 * (2 + 30));
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDisconnectsOnExceedingParallelStreams(final MuxType muxType) {
    // opening 30 streams on normal open rate
    for (int i = 0; i < 30; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isFalse();
    // 20 + 1: giving the Firewall some flexibility to pass 1 extra OPEN frame before closing
    // connection
    assertThat(passedMessages).hasSizeLessThan(20 + 1).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDoesntDisconnectOnAllowedParallelStreams(final MuxType muxType) {
    IntList openedIds = new IntArrayList();
    for (int i = 0; i < 18; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      openedIds.add(i);
      time.addAndGet(112); // ~9 per sec
    }

    Random random = new Random();
    for (int i = 18; i < 200; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      openedIds.add(i);

      IntLists.shuffle(openedIds, random);
      int toRemove = openedIds.removeInt(0);

      writeOneInbound(createCloseReceiverFrame(muxType, toRemove));
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200 * 2 - 18).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatResetStreamFromLocalIsTracked(final MuxType muxType) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));

      channel.writeAndFlush(createResetInitiatorFrame(muxType, i)).await(1000);
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200).doesNotHaveDuplicates();
    assertThat(channel.outboundMessages().stream().map(Object::toString))
        .hasSize(200)
        .doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatResetStreamFromRemoteIsTracked(final MuxType muxType) {
    for (int i = 0; i < 200; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));
      writeOneInbound(createResetReceiverFrame(muxType, i));
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200 * 2).doesNotHaveDuplicates();
  }

  @ParameterizedTest
  @EnumSource(MuxType.class)
  void testThatDisconnectsOnLocalClose(final MuxType muxType) throws InterruptedException {
    // CLOSE (unlike RESET) sent from local doesn't close the stream and still allows remote to
    // write
    // so it shouldn't affect the number of opened tracked streams
    for (int i = 0; i < 25; i++) {
      writeOneInbound(createNewStreamFrame(muxType, i));

      channel.writeAndFlush(createCloseInitiatorFrame(muxType, i)).await(1000);
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isFalse();
    assertThat(passedMessages).hasSizeBetween(18, 22).doesNotHaveDuplicates();
    // hasSizeBetween: giving the Firewall some flexibility to pass 1 extra OPEN frame before
    // closing connection
    assertThat(channel.outboundMessages().stream().map(Object::toString))
        .hasSizeBetween(20, 22)
        .doesNotHaveDuplicates();
  }

  private Object createNewStreamFrame(final MuxType muxType, final long id) {
    return switch (muxType) {
      case MPLEX -> new MplexFrame(createMplexId(id), MplexFlag.NewStream, Unpooled.EMPTY_BUFFER);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id), YamuxType.DATA, Set.of(YamuxFlag.ACK), 0, Unpooled.EMPTY_BUFFER);
    };
  }

  private Object createCloseInitiatorFrame(final MuxType muxType, final long id) {
    return switch (muxType) {
      case MPLEX ->
          new MplexFrame(createMplexId(id), MplexFlag.CloseInitiator, Unpooled.EMPTY_BUFFER);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id), YamuxType.DATA, Set.of(YamuxFlag.FIN), 0, Unpooled.EMPTY_BUFFER);
    };
  }

  private Object createCloseReceiverFrame(final MuxType muxType, final long id) {
    return switch (muxType) {
      case MPLEX ->
          new MplexFrame(createMplexId(id), MplexFlag.CloseReceiver, Unpooled.EMPTY_BUFFER);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id), YamuxType.DATA, Set.of(YamuxFlag.FIN), 0, Unpooled.EMPTY_BUFFER);
    };
  }

  private Object createDataFrame(final MuxType muxType, final long id) {
    final ByteBuf slicedByteBuf = data1K.slice();
    return switch (muxType) {
      case MPLEX -> new MplexFrame(createMplexId(id), MplexFlag.MessageReceiver, slicedByteBuf);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id),
              YamuxType.DATA,
              Set.of(),
              slicedByteBuf.readableBytes(),
              slicedByteBuf);
    };
  }

  private Object createResetInitiatorFrame(final MuxType muxType, final long id) {
    return switch (muxType) {
      case MPLEX ->
          new MplexFrame(createMplexId(id), MplexFlag.ResetInitiator, Unpooled.EMPTY_BUFFER);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id), YamuxType.DATA, Set.of(YamuxFlag.RST), 0, Unpooled.EMPTY_BUFFER);
    };
  }

  private Object createResetReceiverFrame(final MuxType muxType, final long id) {
    return switch (muxType) {
      case MPLEX ->
          new MplexFrame(createMplexId(id), MplexFlag.ResetReceiver, Unpooled.EMPTY_BUFFER);
      case YAMUX ->
          new YamuxFrame(
              createYamuxId(id), YamuxType.DATA, Set.of(YamuxFlag.RST), 0, Unpooled.EMPTY_BUFFER);
    };
  }

  private MplexId createMplexId(final long id) {
    return new MplexId(DUMMY_CHANNEL_ID, id, true);
  }

  private YamuxId createYamuxId(final long id) {
    return new YamuxId(DUMMY_CHANNEL_ID, id);
  }
}
