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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SyncCommitteeContribution
    extends Container5<
        SyncCommitteeContribution, SszUInt64, SszBytes32, SszUInt64, SszBitvector, SszSignature> {

  protected SyncCommitteeContribution(final SyncCommitteeContributionSchema schema) {
    super(schema);
  }

  protected SyncCommitteeContribution(
      final SyncCommitteeContributionSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected SyncCommitteeContribution(
      final SyncCommitteeContributionSchema schema,
      final SszUInt64 slot,
      final SszBytes32 beaconBlockRoot,
      final SszUInt64 subcommitteeIndex,
      final SszBitvector aggregationBits,
      final SszSignature signature) {
    super(schema, slot, beaconBlockRoot, subcommitteeIndex, aggregationBits, signature);
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getField1().get();
  }

  public UInt64 getSubcommitteeIndex() {
    return getField2().get();
  }

  public SszBitvector getAggregationBits() {
    return getField3();
  }

  public BLSSignature getSignature() {
    return getField4().getSignature();
  }

  @Override
  public SyncCommitteeContributionSchema getSchema() {
    return (SyncCommitteeContributionSchema) super.getSchema();
  }
}
