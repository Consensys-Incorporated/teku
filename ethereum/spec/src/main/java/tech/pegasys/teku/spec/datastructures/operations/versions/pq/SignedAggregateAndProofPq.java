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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq.AggregateAndProofPqSchema;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;

/** An aggregate signed by its aggregator with XMSS. */
public class SignedAggregateAndProofPq
    extends Container2<SignedAggregateAndProofPq, AggregateAndProofPq, SszXmssSignature> {

  public static class SignedAggregateAndProofPqSchema
      extends ContainerSchema2<SignedAggregateAndProofPq, AggregateAndProofPq, SszXmssSignature> {

    public SignedAggregateAndProofPqSchema(
        final AggregateAndProofPqSchema aggregateAndProofSchema) {
      super(
          "SignedAggregateAndProofPq",
          namedSchema("message", aggregateAndProofSchema),
          namedSchema("signature", SszXmssSignatureSchema.INSTANCE));
    }

    public AggregateAndProofPqSchema getAggregateAndProofSchema() {
      return (AggregateAndProofPqSchema) getFieldSchema0();
    }

    public SignedAggregateAndProofPq create(
        final AggregateAndProofPq message, final Bytes signature) {
      return new SignedAggregateAndProofPq(this, message, new SszXmssSignature(signature));
    }

    @Override
    public SignedAggregateAndProofPq createFromBackingNode(final TreeNode node) {
      return new SignedAggregateAndProofPq(this, node);
    }
  }

  private SignedAggregateAndProofPq(
      final SignedAggregateAndProofPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private SignedAggregateAndProofPq(
      final SignedAggregateAndProofPqSchema schema,
      final AggregateAndProofPq message,
      final SszXmssSignature signature) {
    super(schema, message, signature);
  }

  public AggregateAndProofPq getMessage() {
    return getField0();
  }

  public Bytes getSignature() {
    return getField1().getBytes();
  }

  @Override
  public SignedAggregateAndProofPqSchema getSchema() {
    return (SignedAggregateAndProofPqSchema) super.getSchema();
  }
}
