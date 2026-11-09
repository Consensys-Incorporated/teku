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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

public interface Eth2PreparedGossipMessageFactory extends PreparedGossipMessageFactory {
  /**
   * Create a {@link PreparedGossipMessage}
   *
   * @param data raw, unprocessed Gossip message data
   * @param valueType The concrete type to deserialize to
   */
  <T extends SszData> PreparedGossipMessage create(
      String topic,
      Bytes data,
      SszSchema<T> valueType,
      NetworkingSpecConfig networkingConfig,
      Optional<UInt64> arrivalTimestamp);
}
