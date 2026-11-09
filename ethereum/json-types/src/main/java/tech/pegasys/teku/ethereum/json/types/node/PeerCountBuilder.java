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

package tech.pegasys.teku.ethereum.json.types.node;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PeerCountBuilder {

  public static final DeserializableTypeDefinition<PeerCount> PEER_COUNT_DATA_TYPE =
      DeserializableTypeDefinition.object(PeerCount.class, PeerCountBuilder.class)
          .initializer(PeerCountBuilder::new)
          .finisher(PeerCountBuilder::build)
          .withField(
              "disconnected",
              UINT64_TYPE,
              PeerCount::getDisconnected,
              PeerCountBuilder::disconnected)
          .withField(
              "connecting", UINT64_TYPE, PeerCount::getConnecting, PeerCountBuilder::connecting)
          .withField("connected", UINT64_TYPE, PeerCount::getConnected, PeerCountBuilder::connected)
          .withField(
              "disconnecting",
              UINT64_TYPE,
              PeerCount::getDisconnecting,
              PeerCountBuilder::disconnecting)
          .build();

  public static final DeserializableTypeDefinition<PeerCount> PEER_COUNT_TYPE =
      DeserializableTypeDefinition.object(PeerCount.class, PeerCountBuilder.class)
          .name("GetPeerCountResponse")
          .initializer(PeerCountBuilder::new)
          .finisher(PeerCountBuilder::build)
          .withField("data", PEER_COUNT_DATA_TYPE, Function.identity(), PeerCountBuilder::peerCount)
          .build();

  private UInt64 disconnected = UInt64.ZERO;
  private UInt64 connecting = UInt64.ZERO;
  private UInt64 connected = UInt64.ZERO;
  private UInt64 disconnecting = UInt64.ZERO;

  public PeerCountBuilder disconnected(final UInt64 disconnected) {
    this.disconnected = disconnected;
    return this;
  }

  public PeerCountBuilder connecting(final UInt64 connecting) {
    this.connecting = connecting;
    return this;
  }

  public PeerCountBuilder connected(final UInt64 connected) {
    this.connected = connected;
    return this;
  }

  public PeerCountBuilder disconnecting(final UInt64 disconnecting) {
    this.disconnecting = disconnecting;
    return this;
  }

  public PeerCountBuilder peerCount(final PeerCount peerCount) {
    this.disconnected = peerCount.getDisconnected();
    this.connecting = peerCount.getConnecting();
    this.connected = peerCount.getConnected();
    this.disconnecting = peerCount.getDisconnecting();
    return this;
  }

  public PeerCount build() {
    return new PeerCount(disconnected, connecting, connected, disconnecting);
  }
}
