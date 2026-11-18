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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A leanXMSS secret key: the 32-byte seed, which is all of the key's secret material. Signing is
 * deterministic from the seed, so the only signing state that must be kept safe is which leaf
 * indices have been used; that bookkeeping belongs to the caller.
 */
public final class XmssSecretKey {

  private final Bytes32 seed;

  private XmssSecretKey(final Bytes32 seed) {
    this.seed = seed;
  }

  public static XmssSecretKey fromBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == XmssConstants.SEED_SIZE,
        "Expected %s bytes for an XMSS seed but got %s",
        XmssConstants.SEED_SIZE,
        bytes.size());
    return new XmssSecretKey(Bytes32.wrap(bytes.toArray()));
  }

  public Bytes32 toBytes() {
    return seed;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof XmssSecretKey that && Objects.equals(seed, that.seed);
  }

  @Override
  public int hashCode() {
    return seed.hashCode();
  }

  @Override
  public String toString() {
    return "XmssSecretKey{redacted}";
  }
}
