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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPeer;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class LibP2PDiscoveryNodeIdExtractor implements DiscoveryNodeIdExtractor {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public Optional<UInt256> calculateDiscoveryNodeId(final Peer peer) {
    if (peer instanceof LibP2PPeer libP2PPeer) {
      try {
        final Bytes libP2PPeerPublicKey = Bytes.wrap(libP2PPeer.getPubKey().raw());
        final Bytes discoveryNodeIdBytes =
            DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER.convertPublicKeyToNodeId(
                libP2PPeerPublicKey);
        return Optional.of(UInt256.fromBytes(discoveryNodeIdBytes));
      } catch (final Exception e) {
        LOG.debug("Failed to calculate discovery node id for peer {}", peer.getId(), e);
        return Optional.empty();
      }
    }

    return Optional.empty();
  }
}
