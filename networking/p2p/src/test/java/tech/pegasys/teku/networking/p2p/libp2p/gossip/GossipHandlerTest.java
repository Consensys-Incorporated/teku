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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kotlin.Unit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class GossipHandlerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final int gossipMaxSize = spec.getNetworkingConfig().getMaxPayloadSize();
  private final Topic topic = new Topic("Testing");
  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final TopicHandler topicHandler = mock(TopicHandler.class);
  private final GossipHandler gossipHandler =
      new GossipHandler(new StubMetricsSystem(), topic, publisher, topicHandler);

  @BeforeEach
  public void setup() {
    when(topicHandler.handleMessage(any()))
        .thenReturn(SafeFuture.completedFuture(ValidationResult.Valid));
    when(topicHandler.getMaxMessageSize()).thenReturn(gossipMaxSize);
    when(publisher.publish(any(), any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  public void apply_valid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void apply_invalid() {
    final Bytes data = Bytes.fromHexString("0x01");
    final MockMessageApi message = new MockMessageApi(data, topic);
    when(topicHandler.handleMessage(any()))
        .thenReturn(SafeFuture.completedFuture(ValidationResult.Invalid));
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void apply_exceedsMaxSize() {
    final Bytes data = Bytes.wrap(new byte[gossipMaxSize + 1]);
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(topicHandler, never()).handleMessage(any());
  }

  @Test
  public void apply_bufferCapacityExceedsMaxSize() {
    ByteBuf data = Unpooled.buffer(gossipMaxSize + 1).writeBytes(new byte[gossipMaxSize]);
    final MockMessageApi message = new MockMessageApi(data, topic);
    final SafeFuture<ValidationResult> result = gossipHandler.apply(message);

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void gossip_newMessage() {
    final Bytes message = Bytes.fromHexString("0x01");
    assertThatSafeFuture(gossipHandler.gossip(message)).isCompleted();
    verify(publisher).publish(toByteBuf(message), topic);
  }

  @Test
  public void gossip_duplicateMessage() { // Deduplication is done a libp2p level.
    final Bytes message = Bytes.fromHexString("0x01");
    assertThatSafeFuture(gossipHandler.gossip(message)).isCompleted();
    assertThatSafeFuture(gossipHandler.gossip(message)).isCompleted();
    verify(publisher, times(2)).publish(toByteBuf(message), topic);
  }

  @Test
  public void gossip_distinctMessages() {
    final Bytes message1 = Bytes.fromHexString("0x01");
    final Bytes message2 = Bytes.fromHexString("0x02");
    assertThatSafeFuture(gossipHandler.gossip(message1)).isCompleted();
    assertThatSafeFuture(gossipHandler.gossip(message2)).isCompleted();
    verify(publisher).publish(toByteBuf(message1), topic);
    verify(publisher).publish(toByteBuf(message2), topic);
  }

  @Test
  public void gossip_forwardsGossipFailures() {
    final Bytes message = Bytes.fromHexString("0x01");
    final SafeFuture<Unit> result = new SafeFuture<>();
    when(publisher.publish(any(), any())).thenReturn(result);
    final SafeFuture<Void> gossipResult = gossipHandler.gossip(message);

    verify(publisher).publish(toByteBuf(message), topic);
    assertThat(gossipResult).isNotCompleted();

    result.completeExceptionally(new RuntimeException("Failed to gossip"));
    assertThatSafeFuture(gossipResult)
        .isCompletedExceptionallyWith(RuntimeException.class)
        .hasMessage("Failed to gossip");
  }

  @Test
  public void
      gossip_returnFutureCompletingOnSuccessfulPublishing() { // Deduplication is done a libp2p
    // level.
    final Bytes message = Bytes.fromHexString("0x01");
    final SafeFuture<Unit> result = new SafeFuture<>();
    when(publisher.publish(any(), any())).thenReturn(result);
    final SafeFuture<Void> gossipResult = gossipHandler.gossip(message);

    verify(publisher).publish(toByteBuf(message), topic);
    assertThat(gossipResult).isNotCompleted();

    result.complete(Unit.INSTANCE);
    assertThat(gossipResult).isCompleted();
  }

  private ByteBuf toByteBuf(final Bytes bytes) {
    return Unpooled.wrappedBuffer(bytes.toArrayUnsafe());
  }
}
