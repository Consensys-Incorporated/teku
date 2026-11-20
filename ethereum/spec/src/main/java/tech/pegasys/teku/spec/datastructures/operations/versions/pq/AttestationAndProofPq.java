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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * A PQ aggregate of one committee with the proof of its attesters' signatures, as the beacon node
 * gives it to an aggregator, who signs it as an {@link AggregateAndProofPq}. Not a consensus type:
 * the validator API's {@code aggregate_attestation} response from PQ.
 */
public class AttestationAndProofPq
    extends Container2<AttestationAndProofPq, AttestationPq, SszByteList> {

  public static class AttestationAndProofPqSchema
      extends ContainerSchema2<AttestationAndProofPq, AttestationPq, SszByteList> {

    public AttestationAndProofPqSchema(
        final AttestationPqSchema attestationSchema,
        final SszByteListSchema<SszByteList> attestationProofSchema) {
      super(
          "AttestationAndProofPq",
          namedSchema("aggregate", attestationSchema),
          namedSchema("proof", attestationProofSchema));
    }

    @SuppressWarnings("unchecked")
    public SszByteListSchema<SszByteList> getAttestationProofSchema() {
      return (SszByteListSchema<SszByteList>) getFieldSchema1();
    }

    public AttestationAndProofPq create(final AttestationPq aggregate, final Bytes proof) {
      return new AttestationAndProofPq(
          this, aggregate, getAttestationProofSchema().fromBytes(proof));
    }

    @Override
    public AttestationAndProofPq createFromBackingNode(final TreeNode node) {
      return new AttestationAndProofPq(this, node);
    }
  }

  private AttestationAndProofPq(
      final AttestationAndProofPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private AttestationAndProofPq(
      final AttestationAndProofPqSchema schema,
      final AttestationPq aggregate,
      final SszByteList proof) {
    super(schema, aggregate, proof);
  }

  public AttestationPq getAggregate() {
    return getField0();
  }

  public Bytes getProof() {
    return getField1().getBytes();
  }

  @Override
  public AttestationAndProofPqSchema getSchema() {
    return (AttestationAndProofPqSchema) super.getSchema();
  }
}
