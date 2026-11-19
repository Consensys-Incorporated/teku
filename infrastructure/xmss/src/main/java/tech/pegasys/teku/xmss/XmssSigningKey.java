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

package tech.pegasys.teku.xmss;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A leanXMSS key loaded into the native library, ready to sign. Loading builds the key's top tree,
 * so keep one open per key rather than loading it for each signature, and close it when done.
 *
 * <p>This class signs whatever leaf it is given. Never sign two different messages at the same
 * leaf: that reveals enough of the key to forge signatures. Callers must record a leaf as used
 * before publishing any signature made with it.
 */
public final class XmssSigningKey implements AutoCloseable {

  private final org.ethereum.leanpq.XmssSecretKey key;
  private final XmssPublicKey publicKey;

  XmssSigningKey(final XmssSecretKey secretKey) {
    this.key = org.ethereum.leanpq.XmssSecretKey.fromSeed(secretKey.toBytes().toArray());
    this.publicKey = XmssPublicKey.fromBytes(Bytes.wrap(key.publicKey()));
  }

  public XmssPublicKey getPublicKey() {
    return publicKey;
  }

  /** Precomputes the signing state for {@code leafIndex} so a later {@link #sign} is fast. */
  public synchronized void prepare(final UInt64 leafIndex) {
    XmssSignature.encodeLeafIndex(leafIndex);
    key.prepare(leafIndex.longValue());
  }

  public synchronized XmssSignature sign(final UInt64 leafIndex, final Bytes32 message) {
    XmssSignature.encodeLeafIndex(leafIndex);
    return XmssSignature.fromBytes(Bytes.wrap(key.sign(message.toArray(), leafIndex.longValue())));
  }

  @Override
  public synchronized void close() {
    key.close();
  }
}
