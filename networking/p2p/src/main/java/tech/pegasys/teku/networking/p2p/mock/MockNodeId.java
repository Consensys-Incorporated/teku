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

package tech.pegasys.teku.networking.p2p.mock;

import com.google.common.base.Strings;
import io.libp2p.etc.encode.Base58;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class MockNodeId extends NodeId {
  private final Bytes bytes;

  public MockNodeId() {
    this(Bytes.fromHexString("0x00", 32));
  }

  public MockNodeId(final int id) {
    this(Bytes.fromHexString(Strings.padStart(Integer.toString(id), 2, '0'), 32));
  }

  public MockNodeId(final Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes toBytes() {
    return bytes;
  }

  @Override
  public String toBase58() {
    return Base58.INSTANCE.encode(bytes.toArrayUnsafe());
  }
}
