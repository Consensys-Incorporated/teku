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
import org.apache.tuweni.bytes.Bytes32;

/** This class represents a BLS12-381 private key. */
public interface SecretKey {

  /**
   * Returns byte secret key representation
   *
   * @return 32 bytes
   */
  Bytes32 toBytes();

  /** Creates a public key corresponding to this secret key */
  PublicKey derivePublicKey();

  /**
   * Generates a Signature from this private key and message.
   *
   * @param message The message to sign, not null
   * @return The Signature, not null
   */
  Signature sign(Bytes message);

  /**
   * Generates a Signature from this private key and message using a custom DST.
   *
   * @param message The message to sign, not null
   * @param dst Domain separation tag/cipher suite to use
   * @return The Signature, not null
   */
  Signature sign(Bytes message, String dst);

  /** Overwrites the key with zeros so that it is no longer in memory */
  void destroy();

  /** Implementation must override */
  @Override
  int hashCode();

  /** Implementation must override */
  @Override
  boolean equals(Object obj);
}
