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

package tech.pegasys.teku.bls.impl;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/** This class represents a BLS12-381 public key. */
public interface PublicKey {

  /**
   * Public key serialization
   *
   * @return byte array of length 48 representation of the public key
   */
  Bytes48 toBytesCompressed();

  /**
   * Verifies the given BLS signature against the message bytes using this public key.
   *
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @return True if the verification is successful, false otherwise.
   */
  default boolean verifySignature(final Signature signature, final Bytes message) {
    return signature.verify(this, message);
  }

  /**
   * Verifies the given BLS signature against the message bytes and DST using this public key.
   *
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @param dst The domain separation tag, not null
   * @return True if the verification is successful, false otherwise.
   */
  default boolean verifySignature(
      final Signature signature, final Bytes message, final String dst) {
    return signature.verify(this, message, dst);
  }

  /**
   * The implementation may be lazy in regards to deserialization of the public key bytes. That
   * method forces immediate deserialization and validation
   *
   * @throws IllegalArgumentException if the public key bytes are invalid
   */
  void forceValidation() throws IllegalArgumentException;

  /**
   * Determine if the key is in the G1 group.
   *
   * @return true if the key is in group, otherwise false.
   */
  boolean isInGroup();

  /**
   * Determine if the key is valid.
   *
   * @return true if the key is valid, otherwise false. The infinity public key is invalid.
   */
  boolean isValid();

  /** Implementation must override */
  @Override
  int hashCode();

  /** Implementation must override */
  @Override
  boolean equals(Object obj);
}
