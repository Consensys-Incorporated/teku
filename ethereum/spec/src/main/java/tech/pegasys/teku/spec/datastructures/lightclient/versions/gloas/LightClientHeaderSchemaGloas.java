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

package tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas;

import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_BLOCK_HASH_GINDEX_GLOAS;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

public class LightClientHeaderSchemaGloas
    extends ContainerSchema3<
        LightClientHeaderGloas, BeaconBlockHeader, SszBytes32, SszBytes32Vector>
    implements LightClientHeaderSchema<LightClientHeaderGloas> {

  public LightClientHeaderSchemaGloas(final String schemaName) {
    super(
        schemaName,
        namedSchema("beacon", BeaconBlockHeader.SSZ_SCHEMA),
        namedSchema("execution_block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            "execution_branch",
            SszBytes32VectorSchema.create(
                MathHelpers.floorLog2(EXECUTION_BLOCK_HASH_GINDEX_GLOAS))));
  }

  public LightClientHeaderGloas create(
      final BeaconBlockHeader beacon,
      final SszBytes32 executionBlockHash,
      final SszBytes32Vector executionBranch) {
    return new LightClientHeaderGloas(this, beacon, executionBlockHash, executionBranch);
  }

  @Override
  public LightClientHeaderGloas create(final BeaconBlockHeader header) {
    return new LightClientHeaderGloas(
        this, header, getFieldSchema1().getDefault(), getFieldSchema2().getDefault());
  }

  @Override
  public LightClientHeaderGloas createFromBackingNode(final TreeNode node) {
    return new LightClientHeaderGloas(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszBytes32VectorSchema<SszBytes32Vector> getExecutionBranchSchema() {
    return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(2);
  }

  @Override
  public LightClientHeaderSchemaGloas toVersionGloasRequired() {
    return this;
  }
}
