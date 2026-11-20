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

package tech.pegasys.teku.spec.logic.versions.pq.crypto;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssConstants;
import tech.pegasys.teku.xmss.XmssPublicKey;
import tech.pegasys.teku.xmss.XmssSignature;

/**
 * The leanXMSS signature scheme's {@code xmss.Verify} and {@code xmss.GetLeafIndex}.
 *
 * <p>{@link #LEAN_XMSS} verifies with leanXMSS, and accepts every signature while {@link
 * XmssConstants#verificationDisabled} is set, as the reference tests' disabled-XMSS mode does.
 * {@link #NO_OP} accepts every signature.
 */
public interface XmssSignatureVerifier {

  /** A signature starts with the leaf index it was produced at, little-endian. */
  int LEAF_INDEX_SIZE = XmssConstants.LEAF_INDEX_SIZE;

  XmssSignatureVerifier NO_OP = (pubkey, leafIndex, message, signature) -> true;

  /** leanXMSS verification. A malformed signature is invalid. */
  XmssSignatureVerifier LEAN_XMSS =
      (pubkey, leafIndex, message, signature) -> {
        if (signature.size() != XmssConstants.SIGNATURE_SIZE) {
          return false;
        }
        return Xmss.verify(
            XmssPublicKey.fromBytes(pubkey),
            leafIndex,
            message,
            XmssSignature.fromBytes(signature));
      };

  /**
   * {@code xmss.Verify}: true only if {@code signature} signs {@code message} at {@code leafIndex}.
   */
  boolean verify(Bytes32 pubkey, UInt64 leafIndex, Bytes32 message, Bytes signature);

  /** {@code xmss.GetLeafIndex}: the leaf index that {@code signature} carries. */
  static UInt64 getLeafIndex(final Bytes signature) {
    return UInt64.valueOf(
        Integer.toUnsignedLong(signature.slice(0, LEAF_INDEX_SIZE).toInt(ByteOrder.LITTLE_ENDIAN)));
  }
}
