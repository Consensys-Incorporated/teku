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

package tech.pegasys.teku.spec.datastructures.epbs.versions.pq;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_DATA_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * A PTC member's payload attestation in PQ, signed with XMSS. It is still gossiped and used by fork
 * choice, but blocks no longer carry payload attestations.
 */
public class PayloadAttestationMessagePq
    extends Container3<
        PayloadAttestationMessagePq, SszUInt64, PayloadAttestationData, SszXmssSignature> {

  public static class PayloadAttestationMessagePqSchema
      extends ContainerSchema3<
          PayloadAttestationMessagePq, SszUInt64, PayloadAttestationData, SszXmssSignature> {

    public PayloadAttestationMessagePqSchema(final SchemaRegistry schemaRegistry) {
      super(
          "PayloadAttestationMessagePq",
          namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("data", schemaRegistry.get(PAYLOAD_ATTESTATION_DATA_SCHEMA)),
          namedSchema("signature", SszXmssSignatureSchema.INSTANCE));
    }

    public PayloadAttestationMessagePq create(
        final UInt64 validatorIndex, final PayloadAttestationData data, final Bytes signature) {
      return new PayloadAttestationMessagePq(
          this, validatorIndex, data, new SszXmssSignature(signature));
    }

    @Override
    public PayloadAttestationMessagePq createFromBackingNode(final TreeNode node) {
      return new PayloadAttestationMessagePq(this, node);
    }
  }

  private PayloadAttestationMessagePq(
      final PayloadAttestationMessagePqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private PayloadAttestationMessagePq(
      final PayloadAttestationMessagePqSchema schema,
      final UInt64 validatorIndex,
      final PayloadAttestationData data,
      final SszXmssSignature signature) {
    super(schema, SszUInt64.of(validatorIndex), data, signature);
  }

  public UInt64 getValidatorIndex() {
    return getField0().get();
  }

  public PayloadAttestationData getData() {
    return getField1();
  }

  public Bytes getSignature() {
    return getField2().getBytes();
  }

  @Override
  public PayloadAttestationMessagePqSchema getSchema() {
    return (PayloadAttestationMessagePqSchema) super.getSchema();
  }
}
