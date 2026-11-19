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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;

/** A single attester's attestation in PQ, signed with XMSS at its target epoch's leaf. */
public class SingleAttestationPq
    extends Container4<
        SingleAttestationPq, SszUInt64, SszUInt64, AttestationData, SszXmssSignature> {

  public static class SingleAttestationPqSchema
      extends ContainerSchema4<
          SingleAttestationPq, SszUInt64, SszUInt64, AttestationData, SszXmssSignature> {

    private SingleAttestationPqSchema() {
      super(
          "SingleAttestationPq",
          namedSchema("committee_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("attester_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszXmssSignatureSchema.INSTANCE));
    }

    @Override
    public SingleAttestationPq createFromBackingNode(final TreeNode node) {
      return new SingleAttestationPq(this, node);
    }
  }

  public static final SingleAttestationPqSchema SSZ_SCHEMA = new SingleAttestationPqSchema();

  private SingleAttestationPq(final SingleAttestationPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SingleAttestationPq(
      final UInt64 committeeIndex,
      final UInt64 attesterIndex,
      final AttestationData data,
      final Bytes signature) {
    super(
        SSZ_SCHEMA,
        SszUInt64.of(committeeIndex),
        SszUInt64.of(attesterIndex),
        data,
        new SszXmssSignature(signature));
  }

  public UInt64 getCommitteeIndex() {
    return getField0().get();
  }

  public UInt64 getAttesterIndex() {
    return getField1().get();
  }

  public AttestationData getData() {
    return getField2();
  }

  public Bytes getSignature() {
    return getField3().getBytes();
  }

  @Override
  public SingleAttestationPqSchema getSchema() {
    return (SingleAttestationPqSchema) super.getSchema();
  }
}
