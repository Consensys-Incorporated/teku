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

package tech.pegasys.teku.network.p2p.jvmlibp2p;

import com.google.protobuf.ByteString;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.PubsubMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.PreparedPubsubMessage;

public class MockMessageApi implements MessageApi {

  private final ByteBuf data;
  private final byte[] from = new byte[] {0x1, 0x2};
  private final List<Topic> topics;

  public MockMessageApi(final ByteBuf data, final Topic topic) {
    this.data = data;
    topics = List.of(topic);
  }

  public MockMessageApi(final Bytes data, final Topic topic) {
    this(Unpooled.wrappedBuffer(data.toArrayUnsafe()), topic);
  }

  @Override
  public ByteBuf getData() {
    return data;
  }

  @Override
  public byte[] getFrom() {
    return from;
  }

  @Override
  public Long getSeqId() {
    return 1L;
  }

  @Override
  public List<Topic> getTopics() {
    return topics;
  }

  @NotNull
  @Override
  public PubsubMessage getOriginalMessage() {
    Message protoMessage =
        Message.newBuilder()
            .addAllTopicIDs(getTopics().stream().map(Topic::getTopic).collect(Collectors.toList()))
            .setData(ByteString.copyFrom(getData().nioBuffer()))
            .build();
    PreparedGossipMessage preparedMessage =
        new PreparedGossipMessage() {
          @Override
          public Bytes getMessageId() {
            return Bytes.wrap(Hash.sha256(protoMessage.getData().toByteArray()));
          }

          @Override
          public DecodedMessageResult getDecodedMessage() {
            final Bytes decoded = Bytes.of(protoMessage.getData().toByteArray());
            return DecodedMessageResult.successful(decoded);
          }

          @Override
          public Bytes getOriginalMessage() {
            return Bytes.wrapByteBuf(data);
          }

          @Override
          public Optional<UInt64> getArrivalTimestamp() {
            return Optional.empty();
          }
        };
    return new PreparedPubsubMessage(protoMessage, preparedMessage);
  }
}
