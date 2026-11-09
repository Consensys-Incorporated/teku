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

package tech.pegasys.teku.spec.datastructures.blocks;

import it.unimi.dsi.fastutil.longs.LongList;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;

public class BeaconBlockSchema
    extends ContainerSchema5<
        BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody>
    implements BlockContainerSchema<BeaconBlock> {

  public BeaconBlockSchema(
      final BeaconBlockBodySchema<?> blockBodySchema, final String containerName) {
    super(
        containerName,
        namedSchema(BeaconBlockFields.SLOT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BeaconBlockFields.PROPOSER_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BeaconBlockFields.PARENT_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BeaconBlockFields.STATE_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BeaconBlockFields.BODY, SszSchema.as(BeaconBlockBody.class, blockBodySchema)));
  }

  @Override
  public BeaconBlock createFromBackingNode(final TreeNode node) {
    return new BeaconBlock(this, node);
  }

  public BeaconBlock create(
      final UInt64 slot,
      final UInt64 proposerIndex,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BeaconBlockBody body) {
    return new BeaconBlock(this, slot, proposerIndex, parentRoot, stateRoot, body);
  }

  public LongList getBlindedNodeGeneralizedIndices() {
    return GIndexUtil.gIdxComposeAll(
        getChildGeneralizedIndex(getFieldIndex(BeaconBlockFields.BODY)),
        getBodySchema().getBlindedNodeGeneralizedIndices());
  }

  public BeaconBlockBodySchema<?> getBodySchema() {
    return (BeaconBlockBodySchema<?>) getChildSchema(getFieldIndex(BeaconBlockFields.BODY));
  }
}
