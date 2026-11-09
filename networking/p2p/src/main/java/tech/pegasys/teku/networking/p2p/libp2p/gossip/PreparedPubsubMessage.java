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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.AbstractPubsubMessage;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.gossip.GossipRouter;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;

/**
 * The bridge class between outer Libp2p {@link PubsubMessage} and inner {@link
 * PreparedGossipMessage}
 *
 * <p>The {@link PreparedGossipMessage} instance created during {@link
 * GossipRouter#getMessageFactory()} invocation can later be accessed when the gossip message is
 * handled: {@link MessageApi#getOriginalMessage()}
 */
public class PreparedPubsubMessage extends AbstractPubsubMessage {

  private final Message protobufMessage;
  private final PreparedGossipMessage preparedMessage;
  private final Supplier<WBytes> cachedMessageId;

  public PreparedPubsubMessage(
      final Message protobufMessage, final PreparedGossipMessage preparedMessage) {
    this.protobufMessage = protobufMessage;
    this.preparedMessage = preparedMessage;
    cachedMessageId =
        Suppliers.memoize(() -> new WBytes(preparedMessage.getMessageId().toArrayUnsafe()));
  }

  @NotNull
  @Override
  public WBytes getMessageId() {
    return cachedMessageId.get();
  }

  @NotNull
  @Override
  public Message getProtobufMessage() {
    return protobufMessage;
  }

  public PreparedGossipMessage getPreparedMessage() {
    return preparedMessage;
  }
}
