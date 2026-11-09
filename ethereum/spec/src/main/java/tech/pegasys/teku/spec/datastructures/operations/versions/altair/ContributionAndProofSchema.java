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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class ContributionAndProofSchema
    extends ContainerSchema3<
        ContributionAndProof, SszUInt64, SyncCommitteeContribution, SszSignature> {

  private ContributionAndProofSchema(
      final NamedSchema<SszUInt64> aggregatorIndexSchema,
      final NamedSchema<SyncCommitteeContribution> contributionSchema,
      final NamedSchema<SszSignature> selectionProofSchema) {
    super("ContributionAndProof", aggregatorIndexSchema, contributionSchema, selectionProofSchema);
  }

  public static ContributionAndProofSchema create(
      final SyncCommitteeContributionSchema contributionSchema) {
    return new ContributionAndProofSchema(
        namedSchema("aggregator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("contribution", contributionSchema),
        namedSchema("selection_proof", SszSignatureSchema.INSTANCE));
  }

  @Override
  public ContributionAndProof createFromBackingNode(final TreeNode node) {
    return new ContributionAndProof(this, node);
  }

  public ContributionAndProof create(
      final UInt64 aggregatorIndex,
      final SyncCommitteeContribution contribution,
      final BLSSignature selectionProof) {
    return new ContributionAndProof(
        this, SszUInt64.of(aggregatorIndex), contribution, new SszSignature(selectionProof));
  }
}
