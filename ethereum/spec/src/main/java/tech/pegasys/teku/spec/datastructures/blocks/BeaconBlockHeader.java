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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BeaconBlockHeader
    extends Container5<BeaconBlockHeader, SszUInt64, SszUInt64, SszBytes32, SszBytes32, SszBytes32>
    implements BeaconBlockSummary {

  public static class BeaconBlockHeaderSchema
      extends ContainerSchema5<
          BeaconBlockHeader, SszUInt64, SszUInt64, SszBytes32, SszBytes32, SszBytes32> {

    public BeaconBlockHeaderSchema() {
      super(
          "BeaconBlockHeader",
          namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("parent_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("body_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public BeaconBlockHeader createFromBackingNode(final TreeNode node) {
      return new BeaconBlockHeader(this, node);
    }
  }

  public static final BeaconBlockHeaderSchema SSZ_SCHEMA = new BeaconBlockHeaderSchema();

  private BeaconBlockHeader(final BeaconBlockHeaderSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlockHeader(
      final UInt64 slot,
      final UInt64 proposerIndex,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Bytes32 bodyRoot) {
    super(
        SSZ_SCHEMA,
        SszUInt64.of(slot),
        SszUInt64.of(proposerIndex),
        SszBytes32.of(parentRoot),
        SszBytes32.of(stateRoot),
        SszBytes32.of(bodyRoot));
  }

  public BeaconBlockHeader(final BeaconBlockHeader header) {
    super(SSZ_SCHEMA, header.getBackingNode());
  }

  public BeaconBlockHeader() {
    super(SSZ_SCHEMA);
  }

  /**
   * Returns the block header associated with this state
   *
   * @param state A beacon state
   * @return The latest block header from the state, with stateRoot pointing to the supplied state
   */
  public static BeaconBlockHeader fromState(final BeaconState state) {
    BeaconBlockHeader latestHeader = state.getLatestBlockHeader();

    if (latestHeader.getStateRoot().isZero()) {
      // If the state root is empty, replace it with the current state root
      final Bytes32 stateRoot = state.hashTreeRoot();
      latestHeader =
          new BeaconBlockHeader(
              latestHeader.getSlot(),
              latestHeader.getProposerIndex(),
              latestHeader.getParentRoot(),
              stateRoot,
              latestHeader.getBodyRoot());
    }

    return latestHeader;
  }

  public static BeaconBlockHeader fromBlock(final BeaconBlock block) {
    return new BeaconBlockHeader(
        block.getSlot(),
        block.getProposerIndex(),
        block.getParentRoot(),
        block.getStateRoot(),
        block.getBodyRoot());
  }

  @Override
  public UInt64 getSlot() {
    return getField0().get();
  }

  @Override
  public UInt64 getProposerIndex() {
    return getField1().get();
  }

  @Override
  public Bytes32 getParentRoot() {
    return getField2().get();
  }

  @Override
  public Bytes32 getStateRoot() {
    return getField3().get();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getField4().get();
  }

  @Override
  public Bytes32 getRoot() {
    return hashTreeRoot();
  }

  @Override
  public BeaconBlockHeaderSchema getSchema() {
    return (BeaconBlockHeaderSchema) super.getSchema();
  }
}
