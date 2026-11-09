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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

class KvStoreTreeNodeSourceTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final V6SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);
  private final KvStoreAccessor accessor =
      MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());

  private final KvStoreTreeNodeSource nodeSource = new KvStoreTreeNodeSource(accessor, schema);

  @Test
  void loadBranchNode_shouldLoadBranchNode() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final CompressedBranchInfo branchInfo =
        storeBranch(
            root,
            dataStructureUtil.randomPositiveInt(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
    assertThat(nodeSource.loadBranchNode(root, 4298)).isEqualTo(branchInfo);
  }

  @Test
  void loadBranchNode_shouldThrowExceptionWhenBranchIsUnknown() {
    assertThatThrownBy(
            () ->
                nodeSource.loadBranchNode(
                    dataStructureUtil.randomBytes32(), dataStructureUtil.randomPositiveInt()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void loadLeafNode_shouldReturnHashWhenDataIsUnknown() {
    final Bytes32 rootHash = dataStructureUtil.randomBytes32();
    assertThat(nodeSource.loadLeafNode(rootHash, 45)).isEqualTo(rootHash);
  }

  @Test
  void loadLeafNode_shouldReturnKnownData() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final Bytes value = dataStructureUtil.randomBytes(78);
    storeLeaf(root, value);

    assertThat(nodeSource.loadLeafNode(root, 428)).isEqualTo(value);
  }

  @Test
  void loadLeafNode_shouldNotReturnBranchData() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    storeBranch(root, 4, dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());

    assertThat(nodeSource.loadLeafNode(root, 23)).isEqualTo(root);
  }

  private void storeLeaf(final Bytes32 root, final Bytes value) {
    try (final KvStoreTransaction transaction = accessor.startTransaction()) {
      transaction.put(schema.getColumnFinalizedStateMerkleTreeLeaves(), root, value);
      transaction.commit();
    }
  }

  private CompressedBranchInfo storeBranch(
      final Bytes32 root, final int depth, final Bytes32... children) {
    try (final KvStoreTransaction transaction = accessor.startTransaction()) {
      final CompressedBranchInfo value = new CompressedBranchInfo(depth, children);
      transaction.put(schema.getColumnFinalizedStateMerkleTreeBranches(), root, value);
      transaction.commit();
      return value;
    }
  }
}
