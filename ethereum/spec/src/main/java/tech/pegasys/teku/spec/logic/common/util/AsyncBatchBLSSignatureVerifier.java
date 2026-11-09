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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncBatchBLSSignatureVerifier implements BLSSignatureVerifier {

  public static final SafeFuture<Boolean> TRUE = SafeFuture.completedFuture(true);
  private final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
  private final List<Bytes> messages = new ArrayList<>();
  private final List<BLSSignature> signatures = new ArrayList<>();

  private final AsyncBLSSignatureVerifier delegate;

  public AsyncBatchBLSSignatureVerifier(final AsyncBLSSignatureVerifier delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    this.publicKeys.add(publicKeys);
    this.messages.add(message);
    this.signatures.add(signature);
    return true;
  }

  @Override
  public boolean verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    this.publicKeys.addAll(publicKeys);
    this.messages.addAll(messages);
    this.signatures.addAll(signatures);
    return true;
  }

  public SafeFuture<Boolean> batchVerify() {
    return delegate.verify(publicKeys, messages, signatures);
  }

  public AsyncBLSSignatureVerifier asAsyncBSLSSignatureVerifier() {
    return new AsyncBLSSignatureVerifier() {
      @Override
      public SafeFuture<Boolean> verify(
          final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
        AsyncBatchBLSSignatureVerifier.this.verify(publicKeys, message, signature);
        return TRUE;
      }

      @Override
      public SafeFuture<Boolean> verify(
          final List<List<BLSPublicKey>> publicKeys,
          final List<Bytes> messages,
          final List<BLSSignature> signatures) {
        AsyncBatchBLSSignatureVerifier.this.verify(publicKeys, messages, signatures);
        return TRUE;
      }
    };
  }
}
