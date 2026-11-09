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

package tech.pegasys.teku.statetransition.validation.signatures;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class SimpleSignatureVerificationService extends SignatureVerificationService {
  @Override
  public SafeFuture<Boolean> verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    final boolean result = BLSSignatureVerifier.SIMPLE.verify(publicKeys, message, signature);
    return SafeFuture.completedFuture(result);
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    return SafeFuture.completedFuture(
        BLSSignatureVerifier.SIMPLE.verify(publicKeys, messages, signatures));
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
