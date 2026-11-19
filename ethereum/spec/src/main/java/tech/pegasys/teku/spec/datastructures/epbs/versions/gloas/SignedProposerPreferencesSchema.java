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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PROPOSER_PREFERENCES_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignatures;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedProposerPreferencesSchema
    extends ContainerSchema2<SignedProposerPreferences, ProposerPreferences, SszByteVector> {

  public SignedProposerPreferencesSchema(final SchemaRegistry schemaRegistry) {
    this(schemaRegistry, SszSignatureSchema.INSTANCE);
  }

  /** For a fork whose signatures are leanXMSS (PQ): takes the signature schema. */
  public SignedProposerPreferencesSchema(
      final SchemaRegistry schemaRegistry, final SszByteVectorSchema<?> signatureSchema) {
    super(
        "SignedProposerPreferences",
        namedSchema("message", schemaRegistry.get(PROPOSER_PREFERENCES_SCHEMA)),
        namedSchema("signature", SszSignatures.asSignatureFieldSchema(signatureSchema)));
  }

  public SignedProposerPreferences create(
      final ProposerPreferences message, final BLSSignature signature) {
    return new SignedProposerPreferences(this, message, signature);
  }

  /** For a schema that takes a leanXMSS signature. */
  public SignedProposerPreferences create(
      final ProposerPreferences message, final SszXmssSignature signature) {
    return new SignedProposerPreferences(this, message, signature);
  }

  @Override
  public SignedProposerPreferences createFromBackingNode(final TreeNode node) {
    return new SignedProposerPreferences(this, node);
  }

  public ProposerPreferencesSchema getMessageSchema() {
    return (ProposerPreferencesSchema) getChildSchema(0);
  }
}
