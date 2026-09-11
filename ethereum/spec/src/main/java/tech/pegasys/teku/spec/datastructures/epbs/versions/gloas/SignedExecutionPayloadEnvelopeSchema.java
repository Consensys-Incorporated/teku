/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedExecutionPayloadEnvelopeSchema
    extends ContainerSchema2<
        SignedExecutionPayloadEnvelope, ExecutionPayloadEnvelope, SszSignature> {

  private final Optional<SszNetworkValidator<SignedExecutionPayloadEnvelope>> networkSszValidator;

  public SignedExecutionPayloadEnvelopeSchema(
      final SchemaRegistry schemaRegistry,
      final Optional<SszNetworkValidator<SignedExecutionPayloadEnvelope>> networkSszValidator) {
    super(
        "SignedExecutionPayloadEnvelope",
        namedSchema("message", schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPE_SCHEMA)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
    this.networkSszValidator = networkSszValidator;
  }

  @Override
  public Optional<SszNetworkValidator<SignedExecutionPayloadEnvelope>> getNetworkSszValidator() {
    return networkSszValidator;
  }

  public SignedExecutionPayloadEnvelope create(
      final ExecutionPayloadEnvelope message, final BLSSignature signature) {
    return new SignedExecutionPayloadEnvelope(this, message, signature);
  }

  @Override
  public SignedExecutionPayloadEnvelope createFromBackingNode(final TreeNode node) {
    return new SignedExecutionPayloadEnvelope(this, node);
  }
}
