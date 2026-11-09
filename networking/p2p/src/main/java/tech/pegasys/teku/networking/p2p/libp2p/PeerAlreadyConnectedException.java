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

package tech.pegasys.teku.networking.p2p.libp2p;

import tech.pegasys.teku.networking.p2p.peer.Peer;

/**
 * Indicates that two connections to the same PeerID were incorrectly established.
 *
 * <p>LibP2P usually detects attempts to establish multiple connections at the same time, but if we
 * have incoming and outgoing connections simultaneously to the same peer, sometimes it slips
 * through. In that case this exception is thrown so that the new connection is terminated before
 * handshakes complete and we are able to identify the situation and return the existing peer.
 */
public class PeerAlreadyConnectedException extends RuntimeException {
  private final Peer peer;

  public PeerAlreadyConnectedException(final Peer peer) {
    super("Already connected to peer " + peer.getId().toBase58());
    this.peer = peer;
  }

  public Peer getPeer() {
    return peer;
  }
}
