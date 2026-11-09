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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SingleAttestation
    extends Container4<SingleAttestation, SszUInt64, SszUInt64, AttestationData, SszSignature>
    implements Attestation {
  public SingleAttestation(final SingleAttestationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SingleAttestation(
      final SingleAttestationSchema schema,
      final UInt64 committeeIndex,
      final UInt64 validatorIndex,
      final AttestationData data,
      final BLSSignature signature) {
    super(
        schema,
        SszUInt64.of(committeeIndex),
        SszUInt64.of(validatorIndex),
        data,
        new SszSignature(signature));
  }

  @Override
  public SingleAttestationSchema getSchema() {
    return (SingleAttestationSchema) super.getSchema();
  }

  @Override
  public AttestationData getData() {
    return getField2();
  }

  @Override
  public SszBitlist getAggregationBits() {
    throw new UnsupportedOperationException("Not supported in SingleAttestation");
  }

  @Override
  public UInt64 getFirstCommitteeIndex() {
    return getField0().get();
  }

  @Override
  public BLSSignature getAggregateSignature() {
    return getField3().getSignature();
  }

  public BLSSignature getSignature() {
    return getField3().getSignature();
  }

  @Override
  public boolean requiresCommitteeBits() {
    return false;
  }

  @Override
  public boolean isSingleAttestation() {
    return true;
  }

  @Override
  public SingleAttestation toSingleAttestationRequired() {
    return this;
  }

  @Override
  public UInt64 getValidatorIndexRequired() {
    return getField1().get();
  }
}
