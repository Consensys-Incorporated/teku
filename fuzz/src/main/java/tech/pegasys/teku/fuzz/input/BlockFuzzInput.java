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

package tech.pegasys.teku.fuzz.input;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BlockFuzzInput extends Container2<BlockFuzzInput, BeaconState, SignedBeaconBlock> {

  public static ContainerSchema2<BlockFuzzInput, BeaconState, SignedBeaconBlock> createSchema(
      final SpecVersion spec) {
    final SchemaDefinitions schemaDefinitions = spec.getSchemaDefinitions();
    return ContainerSchema2.create(
        SszSchema.as(BeaconState.class, schemaDefinitions.getBeaconStateSchema()),
        schemaDefinitions.getSignedBeaconBlockSchema(),
        BlockFuzzInput::new);
  }

  private BlockFuzzInput(
      final ContainerSchema2<BlockFuzzInput, BeaconState, SignedBeaconBlock> type,
      final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockFuzzInput(
      final Spec spec, final BeaconState state, final SignedBeaconBlock signedBlock) {
    super(createSchema(spec.atSlot(state.getSlot())), state, signedBlock);
  }

  public SignedBeaconBlock getSignedBlock() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
