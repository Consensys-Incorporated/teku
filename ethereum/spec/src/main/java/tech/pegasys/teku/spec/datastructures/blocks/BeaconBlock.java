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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BeaconBlock
    extends Container5<BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody>
    implements BeaconBlockSummary, BlockContainer {

  BeaconBlock(final BeaconBlockSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlock(
      final BeaconBlockSchema type,
      final UInt64 slot,
      final UInt64 proposerIndex,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BeaconBlockBody body) {
    super(
        type,
        SszUInt64.of(slot),
        SszUInt64.of(proposerIndex),
        SszBytes32.of(parentRoot),
        SszBytes32.of(stateRoot),
        body);
  }

  public static BeaconBlock fromGenesisState(final Spec spec, final BeaconState genesisState) {
    return fromGenesisState(spec.getGenesisSpec().getSchemaDefinitions(), genesisState);
  }

  public static BeaconBlock fromGenesisState(
      final SchemaDefinitions genesisSchema, final BeaconState genesisState) {
    return new BeaconBlock(
        genesisSchema.getBeaconBlockSchema(),
        UInt64.ZERO,
        UInt64.ZERO,
        Bytes32.ZERO,
        genesisState.hashTreeRoot(),
        genesisSchema.getBeaconBlockBodySchema().createGenesisBody(genesisState));
  }

  public BeaconBlock withStateRoot(final Bytes32 stateRoot) {
    return new BeaconBlock(
        this.getSchema(), getSlot(), getProposerIndex(), getParentRoot(), stateRoot, getBody());
  }

  @Override
  public BeaconBlockSchema getSchema() {
    return (BeaconBlockSchema) super.getSchema();
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

  public BeaconBlockBody getBody() {
    return getField4();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getBody().hashTreeRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return hashTreeRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(this);
  }

  @Override
  public BeaconBlock getBlock() {
    return this;
  }

  @Override
  public boolean isBlinded() {
    return getBody().isBlinded();
  }
}
