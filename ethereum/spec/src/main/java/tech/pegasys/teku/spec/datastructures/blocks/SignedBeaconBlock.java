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

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SignedBeaconBlock extends Container2<SignedBeaconBlock, BeaconBlock, SszSignature>
    implements BeaconBlockSummary, SignedBlockContainer {

  SignedBeaconBlock(final SignedBeaconBlockSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  SignedBeaconBlock(
      final SignedBeaconBlockSchema type, final BeaconBlock message, final BLSSignature signature) {
    super(type, message, new SszSignature(signature));
  }

  public static SignedBeaconBlock create(
      final Spec spec, final BeaconBlock message, final BLSSignature signature) {
    SignedBeaconBlockSchema signedBeaconBlockSchema =
        message.getBody().isBlinded()
            ? spec.atSlot(message.getSlot())
                .getSchemaDefinitions()
                .getSignedBlindedBeaconBlockSchema()
            : spec.atSlot(message.getSlot()).getSchemaDefinitions().getSignedBeaconBlockSchema();
    return new SignedBeaconBlock(signedBeaconBlockSchema, message, signature);
  }

  public SignedBeaconBlockHeader asHeader() {
    return new SignedBeaconBlockHeader(
        new BeaconBlockHeader(
            getSlot(), getProposerIndex(), getParentRoot(), getStateRoot(), getBodyRoot()),
        getSignature());
  }

  public SignedBeaconBlock blind(final SchemaDefinitions schemaDefinitions) {
    if (isBlinded()) {
      return this;
    }

    final TreeNode blindedTree = getBlindedTree();
    return schemaDefinitions.getSignedBlindedBeaconBlockSchema().createFromBackingNode(blindedTree);
  }

  private TreeNode getBlindedTree() {
    final SignedBeaconBlockSchema schema = getSchema();
    final LongList nodeIndicesToBlind = schema.getBlindedNodeGeneralizedIndices();
    final TreeNode backingNode = getBackingNode();
    if (nodeIndicesToBlind.isEmpty()) {
      return backingNode;
    }
    final List<TreeUpdates.Update> updatesList = new ArrayList<>();
    for (long nodeIndexToBlind : nodeIndicesToBlind) {
      final Bytes32 blindedNodeRoot = backingNode.get(nodeIndexToBlind).hashTreeRoot();
      final TreeNode blindedNode = SszBytes32.of(blindedNodeRoot).getBackingNode();
      updatesList.add(new TreeUpdates.Update(nodeIndexToBlind, blindedNode));
    }
    return backingNode.updated(new TreeUpdates(updatesList));
  }

  public SignedBeaconBlock unblind(
      final SchemaDefinitions schemaDefinitions, final ExecutionPayload payload) {
    if (!isBlinded()) {
      return this;
    }

    final TreeNode unblindedTree = getUnblindedTree(payload);
    return schemaDefinitions.getSignedBeaconBlockSchema().createFromBackingNode(unblindedTree);
  }

  private TreeNode getUnblindedTree(final ExecutionPayload payload) {
    final SignedBeaconBlockSchema schema = getSchema();
    final TreeNode backingNode = getBackingNode();
    final LongList blindedNodeIndices = schema.getBlindedNodeGeneralizedIndices();
    if (blindedNodeIndices.isEmpty()) {
      return backingNode;
    }
    final List<TreeUpdates.Update> updatesList = new ArrayList<>();
    final List<TreeNode> unblindedData = payload.getUnblindedTreeNodes();
    for (int i = 0; i < blindedNodeIndices.size(); i++) {
      final long blindedNodeIndex = blindedNodeIndices.getLong(i);
      final Bytes32 expectedRoot = backingNode.get(blindedNodeIndex).hashTreeRoot();
      final TreeNode replacement = unblindedData.get(i);
      checkState(
          expectedRoot.equals(replacement.hashTreeRoot()),
          "Root at index %s in blinded block does not match provided replacement root",
          blindedNodeIndex);
      updatesList.add(new TreeUpdates.Update(blindedNodeIndex, replacement));
    }
    return backingNode.updated(new TreeUpdates(updatesList));
  }

  @Override
  public SignedBeaconBlockSchema getSchema() {
    return (SignedBeaconBlockSchema) super.getSchema();
  }

  public BeaconBlock getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public UInt64 getSlot() {
    return getMessage().getSlot();
  }

  @Override
  public Bytes32 getParentRoot() {
    return getMessage().getParentRoot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return getMessage().getProposerIndex();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getMessage().getBodyRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(getMessage());
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return Optional.of(this);
  }

  /**
   * Get the state root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getStateRoot() {
    return getMessage().getStateRoot();
  }

  /**
   * Get the root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getRoot() {
    return getMessage().hashTreeRoot();
  }

  @Override
  public SignedBeaconBlock getSignedBlock() {
    return this;
  }

  @Override
  public boolean isBlinded() {
    return getMessage().getBody().isBlinded();
  }
}
