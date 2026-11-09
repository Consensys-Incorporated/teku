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

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PeerCount {
  final UInt64 disconnected;
  final UInt64 connecting;
  final UInt64 connected;
  final UInt64 disconnecting;

  PeerCount(
      final UInt64 disconnected,
      final UInt64 connecting,
      final UInt64 connected,
      final UInt64 disconnecting) {
    this.disconnected = disconnected;
    this.connecting = connecting;
    this.connected = connected;
    this.disconnecting = disconnecting;
  }

  public UInt64 getDisconnected() {
    return disconnected;
  }

  public UInt64 getConnecting() {
    return connecting;
  }

  public UInt64 getConnected() {
    return connected;
  }

  public UInt64 getDisconnecting() {
    return disconnecting;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PeerCount that = (PeerCount) o;
    return Objects.equals(disconnected, that.disconnected)
        && Objects.equals(connecting, that.connecting)
        && Objects.equals(connected, that.connected)
        && Objects.equals(disconnecting, that.disconnecting);
  }

  @Override
  public int hashCode() {
    return Objects.hash(disconnected, connecting, connected, disconnecting);
  }
}
