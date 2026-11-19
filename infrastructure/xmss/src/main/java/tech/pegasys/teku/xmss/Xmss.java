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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.leanpq.LeanPq;
import org.ethereum.leanpq.LeanPqException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * leanXMSS signing and verification, backed by the leanVM implementation through {@code
 * io.github.crate-crypto:lean-pq}.
 */
public final class Xmss {

  private static final Logger LOG = LogManager.getLogger();

  private Xmss() {}

  /** Loads {@code secretKey} for signing. The caller must close the returned key. */
  public static XmssSigningKey loadSigningKey(final XmssSecretKey secretKey) {
    return new XmssSigningKey(secretKey);
  }

  /** Derives the public key of {@code secretKey}. This builds the key's top tree, so it is slow. */
  public static XmssPublicKey derivePublicKey(final XmssSecretKey secretKey) {
    try (XmssSigningKey signingKey = loadSigningKey(secretKey)) {
      return signingKey.getPublicKey();
    }
  }

  /**
   * Verifies a signature of {@code message} at {@code leafIndex}, as the spec's {@code Verify}
   * does: the signature must carry {@code leafIndex} and verify under {@code publicKey}.
   */
  public static boolean verify(
      final XmssPublicKey publicKey,
      final UInt64 leafIndex,
      final Bytes32 message,
      final XmssSignature signature) {
    if (XmssConstants.verificationDisabled) {
      LOG.warn("Skipping XMSS verification.");
      return true;
    }
    // The leanVM verifier reads the leaf index from the signature but does not compare it with
    // the one the caller expects, so check it here.
    if (!signature.getLeafIndex().equals(leafIndex)) {
      return false;
    }
    try {
      return LeanPq.xmssVerify(
          publicKey.toBytes().toArrayUnsafe(),
          message.toArrayUnsafe(),
          signature.toBytes().toArrayUnsafe());
    } catch (final LeanPqException e) {
      return false;
    }
  }
}
