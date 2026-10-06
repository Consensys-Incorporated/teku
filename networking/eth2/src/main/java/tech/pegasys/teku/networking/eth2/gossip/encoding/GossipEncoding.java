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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

public interface GossipEncoding {

  GossipEncoding SSZ_SNAPPY = new SszSnappyEncoding(new AircompressorSnappyBlockCompressor());

  /**
   * Get the name of the encoding. This is the name included as part of gossip topic strings.
   *
   * @return The name of this encoding.
   */
  String getName();

  /**
   * Serialize a value for transmission over gossip.
   *
   * @param value The value to serialize.
   * @return The serialized bytes.
   */
  <T extends SszData> Bytes encode(T value);

  /**
   * @return A factory for creating PreparedGossipMessages
   */
  Eth2PreparedGossipMessageFactory createPreparedGossipMessageFactory(
      ForkDigestToMilestone forkDigestToMilestone);

  /**
   * Decodes preprocessed message
   *
   * @param message preprocessed raw bytes message returned earlier by {@link
   *     Eth2PreparedGossipMessageFactory#create(String, Bytes, SszSchema, NetworkingSpecConfig,
   *     Optional)}
   * @param valueType The concrete type to deserialize to
   * @return The deserialized value
   * @throws DecodingException If deserialization fails
   */
  <T extends SszData> T decodeMessage(PreparedGossipMessage message, SszSchema<T> valueType)
      throws DecodingException;

  interface ForkDigestToMilestone {
    static ForkDigestToMilestone fromMap(final Map<Bytes4, SpecMilestone> map) {
      return (forkDigest) -> Optional.ofNullable(map.get(forkDigest));
    }

    Optional<SpecMilestone> getMilestone(final Bytes4 forkDigest);
  }
}
