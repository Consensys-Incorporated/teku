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

package tech.pegasys.teku.networking.p2p.peer;

import org.apache.tuweni.bytes.Bytes;

public abstract class NodeId {

  public abstract Bytes toBytes();

  public abstract String toBase58();

  @Override
  public final int hashCode() {
    return toBytes().hashCode();
  }

  @Override
  public final boolean equals(final Object obj) {
    if (!(obj instanceof NodeId)) {
      return false;
    }
    return toBytes().equals(((NodeId) obj).toBytes());
  }

  @Override
  public final String toString() {
    return toBase58();
  }
}
