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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.PubsubMessage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pubsub.pb.Rpc;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;

class FastMessageIdTest {

  private final Random random = new Random(42);
  private final byte[] digest = randomDigest();

  @Test
  void equalDigestsAreEqualAndHashTheSame() {
    final FastMessageId first = new FastMessageId(digest);
    final FastMessageId second = new FastMessageId(digest.clone());

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void differingDigestsAreNotEqual() {
    final byte[] lastByteDiffers = digest.clone();
    lastByteDiffers[FastMessageId.DIGEST_LENGTH - 1] ^= 1;
    final byte[] firstByteDiffers = digest.clone();
    firstByteDiffers[0] ^= 1;

    final FastMessageId key = new FastMessageId(digest);

    // Same hash (first four bytes agree), so only equals() tells them apart.
    assertThat(new FastMessageId(lastByteDiffers).hashCode()).isEqualTo(key.hashCode());
    assertThat(key).isNotEqualTo(new FastMessageId(lastByteDiffers));
    assertThat(key).isNotEqualTo(new FastMessageId(firstByteDiffers));
  }

  @Test
  void hashCodeIsTheBigEndianIntOfTheFirstFourBytes() {
    final FastMessageId key = new FastMessageId(digest);

    assertThat(key.hashCode()).isEqualTo(ByteBuffer.wrap(digest).getInt());
  }

  @Test
  void hashCodeUsesAllBitsOfEachByte() {
    final byte[] highBits = new byte[FastMessageId.DIGEST_LENGTH];
    highBits[0] = (byte) 0x80;
    highBits[1] = (byte) 0xff;
    highBits[2] = (byte) 0x00;
    highBits[3] = (byte) 0x7f;

    assertThat(new FastMessageId(highBits).hashCode()).isEqualTo(0x80ff007f);
  }

  @Test
  void isNotEqualToOtherTypesOrNull() {
    final FastMessageId key = new FastMessageId(digest);

    assertThat(key).isNotEqualTo(null);
    assertThat(key).isNotEqualTo(digest);
    assertThat(key).isNotEqualTo(Bytes.wrap(digest));
    assertThat(key).isNotEqualTo(new WBytes(digest));
    assertThat(key).isNotEqualTo("FastMessageId");
  }

  @Test
  void rejectsDigestsOfTheWrongLength() {
    assertThatThrownBy(() -> new FastMessageId(new byte[FastMessageId.DIGEST_LENGTH - 1]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FastMessageId(new byte[FastMessageId.DIGEST_LENGTH + 1]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FastMessageId(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void worksAsAHashMapKeyAcrossInstances() {
    final Map<FastMessageId, String> map = new HashMap<>();
    map.put(new FastMessageId(digest), "seen");

    assertThat(map.get(new FastMessageId(digest.clone()))).isEqualTo("seen");
    assertThat(map.containsKey(new FastMessageId(randomDigest()))).isFalse();
  }

  @Test
  void comparesByUnsignedDigestBytesConsistentlyWithEquals() {
    final byte[] low = digest.clone();
    low[5] = 0x01;
    final byte[] high = digest.clone();
    high[5] = (byte) 0xff; // negative as a signed byte, must still sort last

    assertThat(new FastMessageId(low)).isEqualByComparingTo(new FastMessageId(low.clone()));
    assertThat(new FastMessageId(low)).isLessThan(new FastMessageId(high));
    assertThat(new FastMessageId(high)).isGreaterThan(new FastMessageId(low));
  }

  @Test
  void staysLookupableWhenManyKeysShareTheHash() {
    // more keys than HashMap's treeify threshold, all with the same first four bytes
    final Map<FastMessageId, Integer> map = new HashMap<>();
    final byte[][] colliding = new byte[32][];
    for (int i = 0; i < colliding.length; i++) {
      colliding[i] = digest.clone();
      colliding[i][FastMessageId.DIGEST_LENGTH - 1] = (byte) i;
      map.put(new FastMessageId(colliding[i]), i);
    }
    assertThat(map).hasSize(colliding.length);
    for (int i = 0; i < colliding.length; i++) {
      assertThat(map.get(new FastMessageId(colliding[i].clone()))).isEqualTo(i);
    }
    final byte[] absent = digest.clone();
    absent[FastMessageId.DIGEST_LENGTH - 1] = (byte) 0x7f;
    assertThat(map.get(new FastMessageId(absent))).isNull();
  }

  @Test
  void ofWrapsTheMessageDigest() {
    final PubsubMessage message = Mockito.mock(PubsubMessage.class);
    Mockito.when(message.messageSha256()).thenReturn(digest);

    assertThat(FastMessageId.of(message)).isEqualTo(new FastMessageId(digest));
  }

  @Test
  void twoMessagesWithTheSameWireBytesGetEqualKeys() {
    final Rpc.Message wire =
        Rpc.Message.newBuilder()
            .addTopicIDs("/eth2/aabbccdd/beacon_block/ssz_snappy")
            .setData(ByteString.copyFrom(randomDigest()))
            .build();
    final PreparedGossipMessage prepared = Mockito.mock(PreparedGossipMessage.class);
    final PubsubMessage first = new PreparedPubsubMessage(wire, prepared);
    final PubsubMessage second = new PreparedPubsubMessage(wire.toBuilder().build(), prepared);

    assertThat(FastMessageId.of(first)).isEqualTo(FastMessageId.of(second));
    assertThat(FastMessageId.of(first).hashCode()).isEqualTo(FastMessageId.of(second).hashCode());
  }

  @Test
  void toStringShowsTheDigest() {
    assertThat(new FastMessageId(digest).toString())
        .isEqualTo("FastMessageId{" + Bytes.wrap(digest).toHexString() + "}");
  }

  private byte[] randomDigest() {
    final byte[] bytes = new byte[FastMessageId.DIGEST_LENGTH];
    random.nextBytes(bytes);
    return bytes;
  }
}
