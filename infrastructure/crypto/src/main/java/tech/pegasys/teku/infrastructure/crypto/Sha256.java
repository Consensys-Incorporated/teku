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

package tech.pegasys.teku.infrastructure.crypto;

import java.security.MessageDigest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

// MessageDigest can be reused because it resets every time we call digest().
// Reusing significantly reduces memory allocations and have less CPU overhead.
// It is recommended to reuse wherever possible.

public class Sha256 {
  private final MessageDigest messageDigest;

  Sha256(final MessageDigest messageDigest) {
    this.messageDigest = messageDigest;
  }

  public byte[] digest(final byte[] a, final byte[] b) {
    messageDigest.update(a);
    messageDigest.update(b);
    return messageDigest.digest();
  }

  public byte[] digest(final Bytes a, final Bytes b) {
    a.update(messageDigest);
    b.update(messageDigest);
    return messageDigest.digest();
  }

  public byte[] digest(final Bytes a, final Bytes b, final Bytes c) {
    a.update(messageDigest);
    b.update(messageDigest);
    c.update(messageDigest);
    return messageDigest.digest();
  }

  public Bytes32 wrappedDigest(final Bytes a, final Bytes b) {
    return Bytes32.wrap(digest(a, b));
  }
}
