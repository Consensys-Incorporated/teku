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

/** KeyPair represents a public and private key. */
public class KeyPair {
  private final SecretKey secretKey;
  private final PublicKey publicKey;

  public KeyPair(final SecretKey secretKey, final PublicKey publicKey) {
    this.secretKey = secretKey;
    this.publicKey = publicKey;
  }

  public KeyPair(final SecretKey secretKey) {
    this(secretKey, secretKey.derivePublicKey());
  }

  public SecretKey getSecretKey() {
    return secretKey;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }
}
