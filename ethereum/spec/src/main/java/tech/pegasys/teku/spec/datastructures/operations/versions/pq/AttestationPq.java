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

package tech.pegasys.teku.spec.datastructures.operations.versions.pq;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

/**
 * A PQ attestation. It has no signature: the XMSS signatures of its attesters are proven by the
 * block's attestation proof, or by the proof in an aggregate.
 */
public class AttestationPq
    extends Container3<AttestationPq, SszBitlist, AttestationData, SszBitvector>
    implements Attestation {

  AttestationPq(final AttestationPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  AttestationPq(
      final AttestationPqSchema schema,
      final SszBitlist aggregationBits,
      final AttestationData data,
      final SszBitvector committeeBits) {
    super(schema, aggregationBits, data, committeeBits);
  }

  @Override
  public AttestationPqSchema getSchema() {
    return (AttestationPqSchema) super.getSchema();
  }

  @Override
  public SszBitlist getAggregationBits() {
    return getField0();
  }

  @Override
  public AttestationData getData() {
    return getField1();
  }

  @Override
  public Optional<SszBitvector> getCommitteeBits() {
    return Optional.of(getField2());
  }

  /** A PQ attestation has no signature. */
  @Override
  public BLSSignature getAggregateSignature() {
    throw new UnsupportedOperationException("A PQ attestation has no signature");
  }

  @Override
  public Optional<List<UInt64>> getCommitteeIndices() {
    return Optional.of(
        getCommitteeBitsRequired().getAllSetBits().intStream().mapToObj(UInt64::valueOf).toList());
  }

  @Override
  public UInt64 getFirstCommitteeIndex() {
    return UInt64.valueOf(getCommitteeBitsRequired().streamAllSetBits().findFirst().orElseThrow());
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }
}
