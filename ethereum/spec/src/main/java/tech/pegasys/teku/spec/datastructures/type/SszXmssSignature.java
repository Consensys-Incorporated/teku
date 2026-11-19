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
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * A leanXMSS signature as SSZ bytes. Verification lives in {@code infrastructure/xmss}; this type
 * only carries the bytes.
 */
public class SszXmssSignature extends SszByteVectorImpl {

  public SszXmssSignature(final Bytes signature) {
    super(SszXmssSignatureSchema.INSTANCE, signature);
  }

  SszXmssSignature(final TreeNode backingNode) {
    super(SszXmssSignatureSchema.INSTANCE, backingNode);
  }

  /** The all-zero signature, which a self-built bid carries. */
  public static SszXmssSignature empty() {
    return new SszXmssSignature(Bytes.wrap(new byte[SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE]));
  }

  @Override
  public SszXmssSignatureSchema getSchema() {
    return (SszXmssSignatureSchema) super.getSchema();
  }
}
