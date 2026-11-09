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

package tech.pegasys.teku.networking.p2p.gossip;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Semi-processed raw gossip message which can supply Gossip 'message-id'
 *
 * @see TopicHandler#prepareMessage(Bytes, Optional)
 */
public interface PreparedGossipMessage {

  /**
   * Returns the Gossip 'message-id' If the 'message-id' calculation is resource consuming operation
   * is should performed lazily by implementation class
   */
  Bytes getMessageId();

  /**
   * @return Returns the decoded message content
   */
  DecodedMessageResult getDecodedMessage();

  /**
   * Returns the size and leading bytes of the decoded message, so that cheap checks can run before
   * the whole message is decoded. Implementations that don't keep the decoded message should keep
   * this when they compute the message id, rather than decoding the message again.
   *
   * @return the decoded message prefix, or empty if the message can't be decoded
   */
  default Optional<DecodedMessagePrefix> getDecodedMessagePrefix() {
    return getDecodedMessage().getDecodedMessage().map(DecodedMessagePrefix::of);
  }

  Bytes getOriginalMessage();

  Optional<UInt64> getArrivalTimestamp();

  /**
   * The first {@link #MAX_LENGTH} bytes of a decoded message and the size of the whole decoded
   * message.
   */
  record DecodedMessagePrefix(Bytes prefix, int decodedSize) {
    /** Enough for the fixed-size fields at the start of a gossip message, such as a block's slot */
    public static final int MAX_LENGTH = 128;

    public static DecodedMessagePrefix of(final Bytes decodedMessage) {
      // copy so the prefix doesn't keep the whole decoded message reachable
      return new DecodedMessagePrefix(
          Bytes.wrap(
              decodedMessage.slice(0, Math.min(decodedMessage.size(), MAX_LENGTH)).toArray()),
          decodedMessage.size());
    }
  }

  class DecodedMessageResult {
    private final Optional<Bytes> decodedMessage;
    private final Optional<Throwable> decodingException;

    private DecodedMessageResult(
        final Optional<Bytes> decodedMessage, final Optional<Throwable> decodingException) {
      this.decodedMessage = decodedMessage;
      this.decodingException = decodingException;
    }

    public static DecodedMessageResult successful(final Bytes uncompressedMessage) {
      return new DecodedMessageResult(Optional.of(uncompressedMessage), Optional.empty());
    }

    public static DecodedMessageResult failed(final Throwable decodingException) {
      return new DecodedMessageResult(Optional.empty(), Optional.of(decodingException));
    }

    public static DecodedMessageResult failed() {
      return new DecodedMessageResult(Optional.empty(), Optional.empty());
    }

    public Optional<Bytes> getDecodedMessage() {
      return decodedMessage;
    }

    public Bytes getDecodedMessageOrElseThrow() {
      return decodedMessage.orElseThrow(this::getGossipDecodingException);
    }

    private GossipDecodingException getGossipDecodingException() {
      return decodingException
          .map(GossipDecodingException::new)
          .orElseGet(() -> new GossipDecodingException("Failed to decode gossip message"));
    }

    public Optional<Throwable> getDecodingException() {
      return decodingException;
    }
  }

  class GossipDecodingException extends RuntimeException {
    public GossipDecodingException(final String message) {
      super(message);
    }

    public GossipDecodingException(final Throwable cause) {
      super(cause);
    }
  }
}
