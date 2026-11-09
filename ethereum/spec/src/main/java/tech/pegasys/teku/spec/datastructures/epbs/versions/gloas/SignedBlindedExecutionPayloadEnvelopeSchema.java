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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedBlindedExecutionPayloadEnvelopeSchema
    extends ContainerSchema2<
        SignedBlindedExecutionPayloadEnvelope, BlindedExecutionPayloadEnvelope, SszSignature> {

  public SignedBlindedExecutionPayloadEnvelopeSchema(final SchemaRegistry schemaRegistry) {
    super(
        "SignedBlindedExecutionPayloadEnvelope",
        namedSchema("message", schemaRegistry.get(BLINDED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedBlindedExecutionPayloadEnvelope create(
      final BlindedExecutionPayloadEnvelope message, final BLSSignature signature) {
    return new SignedBlindedExecutionPayloadEnvelope(this, message, signature);
  }

  @Override
  public SignedBlindedExecutionPayloadEnvelope createFromBackingNode(final TreeNode node) {
    return new SignedBlindedExecutionPayloadEnvelope(this, node);
  }
}
