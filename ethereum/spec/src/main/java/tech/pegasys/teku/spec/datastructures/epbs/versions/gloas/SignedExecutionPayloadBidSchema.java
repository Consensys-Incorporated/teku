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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_BID_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignatures;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedExecutionPayloadBidSchema
    extends ContainerSchema2<SignedExecutionPayloadBid, ExecutionPayloadBid, SszByteVector> {

  public SignedExecutionPayloadBidSchema(final SchemaRegistry schemaRegistry) {
    this(schemaRegistry, "SignedExecutionPayloadBid", SszSignatureSchema.INSTANCE);
  }

  /**
   * For a fork whose signatures are leanXMSS (PQ): takes the signature schema, and a name that
   * keeps its type apart from the BLS-signed one.
   */
  public SignedExecutionPayloadBidSchema(
      final SchemaRegistry schemaRegistry,
      final String containerName,
      final SszByteVectorSchema<?> signatureSchema) {
    super(
        containerName,
        namedSchema("message", schemaRegistry.get(EXECUTION_PAYLOAD_BID_SCHEMA)),
        namedSchema("signature", SszSignatures.asSignatureFieldSchema(signatureSchema)));
  }

  public SignedExecutionPayloadBid create(
      final ExecutionPayloadBid message, final BLSSignature signature) {
    return new SignedExecutionPayloadBid(this, message, signature);
  }

  /** For a schema that takes a leanXMSS signature. */
  public SignedExecutionPayloadBid create(
      final ExecutionPayloadBid message, final SszXmssSignature signature) {
    return new SignedExecutionPayloadBid(this, message, signature);
  }

  /**
   * A self-built bid, with the empty signature of this schema's kind: G2_POINT_AT_INFINITY for BLS,
   * the empty XMSSSignature (all zero bytes) from PQ.
   */
  public SignedExecutionPayloadBid createSelfBuilt(final ExecutionPayloadBid message) {
    return getChildSchema(1) instanceof SszXmssSignatureSchema
        ? create(message, SszXmssSignature.empty())
        : create(message, BLSSignature.infinity());
  }

  @Override
  public SignedExecutionPayloadBid createFromBackingNode(final TreeNode node) {
    return new SignedExecutionPayloadBid(this, node);
  }

  public ExecutionPayloadBidSchema getMessageSchema() {
    return (ExecutionPayloadBidSchema) getChildSchema(0);
  }
}
