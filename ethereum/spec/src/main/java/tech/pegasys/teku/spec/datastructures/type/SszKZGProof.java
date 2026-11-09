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

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;

public class SszKZGProof extends SszByteVectorImpl {

  private final Supplier<KZGProof> kzgProof;

  public SszKZGProof(final Bytes48 kzgProofBytes) {
    super(SszKZGProofSchema.INSTANCE, kzgProofBytes);
    this.kzgProof = Suppliers.memoize(this::createKZGProof);
  }

  public SszKZGProof(final KZGProof kzgProof) {
    super(SszKZGProofSchema.INSTANCE, kzgProof.getBytesCompressed());
    this.kzgProof = Suppliers.memoize(this::createKZGProof);
  }

  SszKZGProof(final TreeNode backingNode) {
    super(SszKZGProofSchema.INSTANCE, backingNode);
    this.kzgProof = Suppliers.memoize(this::createKZGProof);
  }

  public KZGProof getKZGProof() {
    return kzgProof.get();
  }

  private KZGProof createKZGProof() {
    return KZGProof.fromBytesCompressed(getBytes());
  }

  @Override
  public Bytes48 getBytes() {
    return Bytes48.wrap(super.getBytes());
  }

  @Override
  public SszKZGProofSchema getSchema() {
    return (SszKZGProofSchema) super.getSchema();
  }
}
