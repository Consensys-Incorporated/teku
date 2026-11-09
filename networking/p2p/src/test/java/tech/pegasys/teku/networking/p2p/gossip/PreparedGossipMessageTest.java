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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage.DecodedMessagePrefix;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage.DecodedMessageResult;

class PreparedGossipMessageTest {

  @Test
  void decodedMessagePrefix_shouldKeepLeadingBytesAndSize() {
    final Bytes decoded = Bytes.random(DecodedMessagePrefix.MAX_LENGTH * 3);

    final DecodedMessagePrefix prefix = DecodedMessagePrefix.of(decoded);

    assertThat(prefix.prefix()).isEqualTo(decoded.slice(0, DecodedMessagePrefix.MAX_LENGTH));
    assertThat(prefix.decodedSize()).isEqualTo(decoded.size());
  }

  @Test
  void decodedMessagePrefix_shouldCopyRatherThanReferenceDecodedMessage() {
    final byte[] decodedArray = new byte[DecodedMessagePrefix.MAX_LENGTH * 3];

    final DecodedMessagePrefix prefix = DecodedMessagePrefix.of(Bytes.wrap(decodedArray));
    decodedArray[0] = 1;

    assertThat(prefix.prefix().get(0)).isZero();
    assertThat(prefix.prefix().toArrayUnsafe()).hasSize(DecodedMessagePrefix.MAX_LENGTH);
  }

  @Test
  void decodedMessagePrefix_shouldKeepWholeMessageWhenShorterThanMaxLength() {
    final Bytes decoded = Bytes.random(10);

    assertThat(DecodedMessagePrefix.of(decoded)).isEqualTo(new DecodedMessagePrefix(decoded, 10));
  }

  @Test
  void getDecodedMessagePrefix_shouldDefaultToPrefixOfDecodedMessage() {
    final Bytes decoded = Bytes.random(500);

    assertThat(
            messageDecodingTo(DecodedMessageResult.successful(decoded)).getDecodedMessagePrefix())
        .contains(DecodedMessagePrefix.of(decoded));
    assertThat(messageDecodingTo(DecodedMessageResult.failed()).getDecodedMessagePrefix())
        .isEmpty();
  }

  private static PreparedGossipMessage messageDecodingTo(final DecodedMessageResult result) {
    return new PreparedGossipMessage() {
      @Override
      public Bytes getMessageId() {
        return Bytes.EMPTY;
      }

      @Override
      public DecodedMessageResult getDecodedMessage() {
        return result;
      }

      @Override
      public Bytes getOriginalMessage() {
        return Bytes.EMPTY;
      }

      @Override
      public Optional<UInt64> getArrivalTimestamp() {
        return Optional.empty();
      }
    };
  }
}
