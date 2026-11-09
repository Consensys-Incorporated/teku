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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage.GossipDecodingException;

class SszSnappyEncoding implements GossipEncoding {
  private static final String NAME = "ssz_snappy";
  private final SnappyCompressor snappyCompressor;
  private final SszGossipCodec sszCodec = new SszGossipCodec();

  public SszSnappyEncoding(final SnappyCompressor snappyCompressor) {
    this.snappyCompressor = snappyCompressor;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public <T extends SszData> Bytes encode(final T value) {
    return snappyCompressor.compress(sszCodec.encode(value));
  }

  @Override
  public <T extends SszData> T decodeMessage(
      final PreparedGossipMessage message, final SszSchema<T> valueType) throws DecodingException {
    try {
      return sszCodec.decode(message.getDecodedMessage().getDecodedMessageOrElseThrow(), valueType);
    } catch (GossipDecodingException e) {
      throw new DecodingException("Failed to decode gossip message", e);
    }
  }

  @Override
  public Eth2PreparedGossipMessageFactory createPreparedGossipMessageFactory(
      final ForkDigestToMilestone forkDigestToMilestone) {
    return new SnappyPreparedGossipMessageFactory(snappyCompressor, forkDigestToMilestone);
  }
}
