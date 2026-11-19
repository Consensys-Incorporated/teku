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

package tech.pegasys.teku.spec.datastructures.type;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;

/**
 * Signature fields that hold either a BLS signature ({@link SszSignature}) or, from PQ, a leanXMSS
 * signature ({@link SszXmssSignature}). A container types such a field as {@link SszByteVector} and
 * its schema decides which one it holds.
 */
public final class SszSignatures {

  private SszSignatures() {}

  /** The field's schema, typed as the field. */
  @SuppressWarnings("unchecked")
  public static SszSchema<SszByteVector> asSignatureFieldSchema(
      final SszByteVectorSchema<?> signatureSchema) {
    return (SszSchema<SszByteVector>) (SszSchema<?>) signatureSchema;
  }

  public static boolean isXmss(final SszByteVector signature) {
    return signature instanceof SszXmssSignature;
  }

  /**
   * @throws UnsupportedOperationException if the field holds an XMSS signature
   */
  public static BLSSignature blsSignature(final SszByteVector signature) {
    if (signature instanceof SszSignature blsSignature) {
      return blsSignature.getSignature();
    }
    throw new UnsupportedOperationException("XMSS-signed: there is no BLS signature");
  }

  /**
   * @throws UnsupportedOperationException if the field holds a BLS signature
   */
  public static Bytes xmssSignature(final SszByteVector signature) {
    if (signature instanceof SszXmssSignature xmssSignature) {
      return xmssSignature.getBytes();
    }
    throw new UnsupportedOperationException("BLS-signed: there is no XMSS signature");
  }
}
