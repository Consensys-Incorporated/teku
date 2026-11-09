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

package tech.pegasys.teku.spec.datastructures.operations.versions.electra;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class AttestationElectra
    extends Container4<AttestationElectra, SszBitlist, AttestationData, SszSignature, SszBitvector>
    implements Attestation {

  public AttestationElectra(final AttestationElectraSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationElectra(
      final AttestationElectraSchema schema,
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final SszBitvector committeeBits) {
    super(schema, aggregationBits, data, new SszSignature(signature), committeeBits);
  }

  @Override
  public AttestationElectraSchema getSchema() {
    return (AttestationElectraSchema) super.getSchema();
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
    return Optional.of(getField3());
  }

  @Override
  public BLSSignature getAggregateSignature() {
    return getField2().getSignature();
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
