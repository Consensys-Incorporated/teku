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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.libp2p.pubsub.PubsubMessage;
import org.junit.jupiter.api.Test;
import pubsub.pb.Rpc;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipWireValidator.InvalidGossipMessageException;

class GossipWireValidatorTest {

  private final GossipWireValidator validator = new GossipWireValidator();

  private PubsubMessage msgWith(final Rpc.Message proto) {
    final PubsubMessage msg = mock(PubsubMessage.class);
    when(msg.getProtobufMessage()).thenReturn(proto);
    return msg;
  }

  @Test
  void acceptsCleanMessage() {
    final Rpc.Message proto =
        Rpc.Message.newBuilder().setData(ByteString.copyFromUtf8("payload")).build();
    assertThatCode(() -> validator.validate(msgWith(proto))).doesNotThrowAnyException();
  }

  @Test
  void rejectsFrom() {
    final Rpc.Message proto =
        Rpc.Message.newBuilder().setFrom(ByteString.copyFromUtf8("peer")).build();
    assertThatThrownBy(() -> validator.validate(msgWith(proto)))
        .isInstanceOf(InvalidGossipMessageException.class)
        .hasMessageContaining("from");
  }

  @Test
  void rejectsSignature() {
    final Rpc.Message proto =
        Rpc.Message.newBuilder().setSignature(ByteString.copyFromUtf8("sig")).build();
    assertThatThrownBy(() -> validator.validate(msgWith(proto)))
        .isInstanceOf(InvalidGossipMessageException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void rejectsSeqno() {
    final Rpc.Message proto =
        Rpc.Message.newBuilder().setSeqno(ByteString.copyFromUtf8("1")).build();
    assertThatThrownBy(() -> validator.validate(msgWith(proto)))
        .isInstanceOf(InvalidGossipMessageException.class)
        .hasMessageContaining("seqno");
  }

  @Test
  void rejectsKey() {
    final Rpc.Message proto =
        Rpc.Message.newBuilder().setKey(ByteString.copyFromUtf8("pubkey")).build();
    assertThatThrownBy(() -> validator.validate(msgWith(proto)))
        .isInstanceOf(InvalidGossipMessageException.class)
        .hasMessageContaining("key");
  }
}
