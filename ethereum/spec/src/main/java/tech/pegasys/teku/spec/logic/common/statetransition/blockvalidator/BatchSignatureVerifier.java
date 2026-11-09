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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;

public interface BatchSignatureVerifier extends BLSSignatureVerifier {

  BatchSignatureVerifier NOOP =
      new BatchSignatureVerifier() {
        @Override
        public boolean verify(
            final List<BLSPublicKey> publicKeys,
            final Bytes message,
            final BLSSignature signature) {
          return true;
        }

        @Override
        public boolean verify(
            final List<List<BLSPublicKey>> publicKeys,
            final List<Bytes> messages,
            final List<BLSSignature> signatures) {
          return true;
        }

        @Override
        public boolean batchVerify() {
          return true;
        }
      };

  @Override
  boolean verify(List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature);

  @Override
  boolean verify(
      List<List<BLSPublicKey>> publicKeys, List<Bytes> messages, List<BLSSignature> signatures);

  boolean batchVerify();
}
