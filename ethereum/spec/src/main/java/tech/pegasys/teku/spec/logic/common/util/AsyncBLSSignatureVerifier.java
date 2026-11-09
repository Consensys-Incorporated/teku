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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface AsyncBLSSignatureVerifier {
  static AsyncBLSSignatureVerifier wrap(final BLSSignatureVerifier syncVerifier) {
    return new AsyncBLSSignatureVerifier() {
      @Override
      public SafeFuture<Boolean> verify(
          final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
        final boolean result = syncVerifier.verify(publicKeys, message, signature);
        return SafeFuture.completedFuture(result);
      }

      @Override
      public SafeFuture<Boolean> verify(
          final List<List<BLSPublicKey>> publicKeys,
          final List<Bytes> messages,
          final List<BLSSignature> signatures) {
        final boolean result = syncVerifier.verify(publicKeys, messages, signatures);
        return SafeFuture.completedFuture(result);
      }
    };
  }

  /**
   * Verifies an aggregate BLS signature against a message using the list of public keys. In case of
   * non-aggregate signature [publicKeys] list should contain just a single entry
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  SafeFuture<Boolean> verify(List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature);

  /** Shortcut to {@link #verify(List, Bytes, BLSSignature)} for non-aggregate case */
  default SafeFuture<Boolean> verify(
      final BLSPublicKey publicKey, final Bytes message, final BLSSignature signature) {
    return verify(Collections.singletonList(publicKey), message, signature);
  }

  SafeFuture<Boolean> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures);
}
