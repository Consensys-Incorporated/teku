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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SyncAggregateSchema
    extends ContainerSchema2<SyncAggregate, SszBitvector, SszSignature> {

  private SyncAggregateSchema(
      final NamedSchema<SszBitvector> syncCommitteeBitsSchema,
      final NamedSchema<SszSignature> sszSignatureSchema) {
    super("SyncAggregate", syncCommitteeBitsSchema, sszSignatureSchema);
  }

  public static SyncAggregateSchema create(final int syncCommitteeSize) {
    return new SyncAggregateSchema(
        namedSchema("sync_committee_bits", SszBitvectorSchema.create(syncCommitteeSize)),
        namedSchema("sync_committee_signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SyncAggregate createFromBackingNode(final TreeNode node) {
    return new SyncAggregate(this, node);
  }

  public SszBitvectorSchema<SszBitvector> getSyncCommitteeBitsSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema0();
  }

  public SyncAggregate createEmpty() {
    return new SyncAggregate(
        this, getSyncCommitteeBitsSchema().ofBits(), new SszSignature(BLSSignature.infinity()));
  }

  public SyncAggregate create(
      final Iterable<Integer> participantIndices, final BLSSignature signature) {
    return new SyncAggregate(
        this, getSyncCommitteeBitsSchema().ofBits(participantIndices), new SszSignature(signature));
  }
}
