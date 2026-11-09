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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTING_INDICES_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class IndexedAttestationSchema
    extends ContainerSchema3<IndexedAttestation, SszUInt64List, AttestationData, SszSignature> {

  public IndexedAttestationSchema(final String containerName, final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema("attesting_indices", schemaRegistry.get(ATTESTING_INDICES_SCHEMA)),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  protected IndexedAttestationSchema(
      final String containerName,
      final boolean[] activeFields,
      final NamedSchema<SszUInt64List> attestingIndicesSchema,
      final NamedSchema<AttestationData> dataSchema,
      final NamedSchema<SszSignature> signatureSchema) {
    super(containerName, activeFields, attestingIndicesSchema, dataSchema, signatureSchema);
  }

  public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<?>) super.getFieldSchema0();
  }

  @Override
  public IndexedAttestation createFromBackingNode(final TreeNode node) {
    return new IndexedAttestation(this, node);
  }

  public IndexedAttestation create(
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final BLSSignature signature) {
    return new IndexedAttestation(this, attestingIndices, data, signature);
  }
}
