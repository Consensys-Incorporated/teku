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
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * An aggregate in PQ. Aggregators come from the state's aggregation-committee window, so there is
 * no selection proof; {@code proof} proves the XMSS signatures of the aggregate's attesters.
 */
public class AggregateAndProofPq
    extends Container3<AggregateAndProofPq, SszUInt64, AttestationPq, SszByteList> {

  public static class AggregateAndProofPqSchema
      extends ContainerSchema3<AggregateAndProofPq, SszUInt64, AttestationPq, SszByteList> {

    public AggregateAndProofPqSchema(
        final AttestationPqSchema attestationSchema,
        final SszByteListSchema<SszByteList> attestationProofSchema) {
      super(
          "AggregateAndProofPq",
          namedSchema("aggregator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("aggregate", attestationSchema),
          namedSchema("proof", attestationProofSchema));
    }

    public AttestationPqSchema getAttestationSchema() {
      return (AttestationPqSchema) getFieldSchema1();
    }

    public SszByteListSchema<SszByteList> getAttestationProofSchema() {
      return (SszByteListSchema<SszByteList>) getFieldSchema2();
    }

    public AggregateAndProofPq create(
        final UInt64 aggregatorIndex, final AttestationPq aggregate, final Bytes proof) {
      return new AggregateAndProofPq(
          this, aggregatorIndex, aggregate, getAttestationProofSchema().fromBytes(proof));
    }

    @Override
    public AggregateAndProofPq createFromBackingNode(final TreeNode node) {
      return new AggregateAndProofPq(this, node);
    }
  }

  private AggregateAndProofPq(final AggregateAndProofPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private AggregateAndProofPq(
      final AggregateAndProofPqSchema schema,
      final UInt64 aggregatorIndex,
      final AttestationPq aggregate,
      final SszByteList proof) {
    super(schema, SszUInt64.of(aggregatorIndex), aggregate, proof);
  }

  public UInt64 getAggregatorIndex() {
    return getField0().get();
  }

  public AttestationPq getAggregate() {
    return getField1();
  }

  public Bytes getProof() {
    return getField2().getBytes();
  }

  @Override
  public AggregateAndProofPqSchema getSchema() {
    return (AggregateAndProofPqSchema) super.getSchema();
  }
}
