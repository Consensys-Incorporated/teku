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

package tech.pegasys.teku.networking.p2p.reputation;

import java.util.Optional;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

public interface ReputationManager {
  ReputationManager NOOP =
      new ReputationManager() {
        @Override
        public void reportInitiatedConnectionFailed(PeerAddress peerAddress) {}

        @Override
        public boolean isConnectionInitiationAllowed(PeerAddress peerAddress) {
          return true;
        }

        @Override
        public Optional<DisconnectReason> getInboundConnectionRejectionReason(
            final PeerAddress peerAddress) {
          return Optional.empty();
        }

        @Override
        public void reportInitiatedConnectionSuccessful(PeerAddress peerAddress) {}

        @Override
        public void reportDisconnection(
            PeerAddress peerAddress, Optional<DisconnectReason> reason, boolean locallyInitiated) {}

        @Override
        public boolean adjustReputation(PeerAddress peerAddress, ReputationAdjustment effect) {
          return false;
        }
      };

  void reportInitiatedConnectionFailed(final PeerAddress peerAddress);

  boolean isConnectionInitiationAllowed(final PeerAddress peerAddress);

  /**
   * Returns the reason to use when rejecting an inbound connection from this peer, or empty when
   * the peer should not be rejected. This excludes temporary outbound dial cooldowns.
   */
  Optional<DisconnectReason> getInboundConnectionRejectionReason(final PeerAddress peerAddress);

  void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress);

  void reportDisconnection(
      final PeerAddress peerAddress,
      final Optional<DisconnectReason> reason,
      final boolean locallyInitiated);

  /**
   * @return boolean representing whether the manager should disconnect.
   */
  boolean adjustReputation(final PeerAddress peerAddress, final ReputationAdjustment effect);
}
