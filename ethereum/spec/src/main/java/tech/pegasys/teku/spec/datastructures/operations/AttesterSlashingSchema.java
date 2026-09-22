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

package tech.pegasys.teku.spec.datastructures.operations;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_ATTESTATION_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class AttesterSlashingSchema
    extends ContainerSchema2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {

  public AttesterSlashingSchema(final String containerName, final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema("attestation_1", schemaRegistry.get(INDEXED_ATTESTATION_SCHEMA)),
        namedSchema("attestation_2", schemaRegistry.get(INDEXED_ATTESTATION_SCHEMA)));
  }

  @Override
  public AttesterSlashing createFromBackingNode(final TreeNode node) {
    return new AttesterSlashing(this, node);
  }

  public AttesterSlashing create(
      final IndexedAttestation attestation1, final IndexedAttestation attestation2) {
    return new AttesterSlashing(this, attestation1, attestation2);
  }
}
