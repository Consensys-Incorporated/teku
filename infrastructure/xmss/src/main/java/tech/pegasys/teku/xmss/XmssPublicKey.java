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

/** A leanXMSS public key: the top tree's root (16 bytes) then the public parameter (16 bytes). */
public final class XmssPublicKey {

  private final Bytes32 bytes;

  private XmssPublicKey(final Bytes32 bytes) {
    this.bytes = bytes;
  }

  public static XmssPublicKey fromBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == XmssConstants.PUBLIC_KEY_SIZE,
        "Expected %s bytes for an XMSS public key but got %s",
        XmssConstants.PUBLIC_KEY_SIZE,
        bytes.size());
    return new XmssPublicKey(Bytes32.wrap(bytes.toArray()));
  }

  public static XmssPublicKey fromHexString(final String hex) {
    return fromBytes(Bytes.fromHexString(hex));
  }

  public Bytes32 toBytes() {
    return bytes;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof XmssPublicKey that && Objects.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  @Override
  public String toString() {
    return bytes.toHexString();
  }
}
