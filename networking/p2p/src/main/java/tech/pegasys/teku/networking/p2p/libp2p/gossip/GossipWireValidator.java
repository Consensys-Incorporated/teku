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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc;

/**
 * Validates Gossip messages at the level of Protobuf structures Rejects messages with prohibited
 * Gossip fields: {@code from, signature, seqno, key}
 */
public class GossipWireValidator implements PubsubRouterMessageValidator {

  public static class InvalidGossipMessageException extends IllegalArgumentException {
    public InvalidGossipMessageException(final String s) {
      super(s);
    }
  }

  @Override
  public void validate(final @NotNull PubsubMessage pubsubMessage) {
    Rpc.Message message = pubsubMessage.getProtobufMessage();
    if (message.hasFrom()) {
      throw new InvalidGossipMessageException("The message has prohibited 'from' field: ");
    }
    if (message.hasSignature()) {
      throw new InvalidGossipMessageException("The message has prohibited 'signature' field");
    }
    if (message.hasSeqno()) {
      throw new InvalidGossipMessageException("The message has prohibited 'seqno' field");
    }
    if (message.hasKey()) {
      throw new InvalidGossipMessageException("The message has prohibited 'key' field");
    }
  }
}
