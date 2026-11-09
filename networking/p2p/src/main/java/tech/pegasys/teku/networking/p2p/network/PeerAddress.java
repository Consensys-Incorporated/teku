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

package tech.pegasys.teku.networking.p2p.network;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class PeerAddress {
  private final NodeId id;

  public PeerAddress(final NodeId id) {
    this.id = id;
  }

  public String toExternalForm() {
    return toString();
  }

  public NodeId getId() {
    return id;
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> as(final Class<T> clazz) {
    if (clazz.isInstance(this)) {
      return Optional.of((T) this);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return id.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PeerAddress that = (PeerAddress) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
