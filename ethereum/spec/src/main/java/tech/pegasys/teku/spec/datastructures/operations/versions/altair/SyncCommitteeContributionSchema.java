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

import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SyncCommitteeContributionSchema
    extends ContainerSchema5<
        SyncCommitteeContribution, SszUInt64, SszBytes32, SszUInt64, SszBitvector, SszSignature> {

  private SyncCommitteeContributionSchema(
      final NamedSchema<SszUInt64> slotSchema,
      final NamedSchema<SszBytes32> beaconBlockRootSchema,
      final NamedSchema<SszUInt64> subcommitteeIndexSchema,
      final NamedSchema<SszBitvector> aggregationBitsSchema,
      final NamedSchema<SszSignature> signatureSchema) {
    super(
        "SyncCommitteeContribution",
        slotSchema,
        beaconBlockRootSchema,
        subcommitteeIndexSchema,
        aggregationBitsSchema,
        signatureSchema);
  }

  public static SyncCommitteeContributionSchema create(final SpecConfigAltair specConfig) {
    return new SyncCommitteeContributionSchema(
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("subcommittee_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "aggregation_bits",
            SszBitvectorSchema.create(
                specConfig.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SyncCommitteeContribution create(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 subcommitteeIndex,
      final SszBitvector aggregationBits,
      final BLSSignature signature) {
    return new SyncCommitteeContribution(
        this,
        SszUInt64.of(slot),
        SszBytes32.of(beaconBlockRoot),
        SszUInt64.of(subcommitteeIndex),
        aggregationBits,
        new SszSignature(signature));
  }

  @Override
  public SyncCommitteeContribution createFromBackingNode(final TreeNode node) {
    return new SyncCommitteeContribution(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszBitvectorSchema<SszBitvector> getAggregationBitsSchema() {
    return (SszBitvectorSchema<SszBitvector>) getChildSchema(3);
  }
}
