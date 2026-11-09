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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

class KvStoreTreeNodeStoreTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Set<Bytes32> knownBranchCache = new HashSet<>();
  private final KvStoreAccessor db = mock(KvStoreAccessor.class);
  private final KvStoreTransaction transaction = mock(KvStoreTransaction.class);
  private final SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);

  private final KvStoreTreeNodeStore store =
      new KvStoreTreeNodeStore(knownBranchCache, db, transaction, schema);

  @BeforeEach
  void setUp() {
    when(db.get(any(), any())).thenReturn(Optional.empty());
  }

  @Test
  void canSkipBranch_shouldSkipBranchWhenInKnownBranchCache() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    knownBranchCache.add(root);

    assertThat(store.canSkipBranch(root, 3)).isTrue();
    assertThat(store.getSkippedBranchNodeCount()).isEqualTo(1);
    assertThat(store.getStoredBranchNodeCount()).isZero();
    assertThat(store.getStoredLeafNodeCount()).isZero();
  }

  @Test
  void canSkipBranch_shouldNotSkipBranchWhenNotInKnownBranchCacheOrDb() {
    final Bytes32 root = dataStructureUtil.randomBytes32();

    assertThat(store.canSkipBranch(root, 3)).isFalse();
    assertThat(store.getSkippedBranchNodeCount()).isZero();
    assertThat(store.getStoredBranchNodeCount()).isZero();
    assertThat(store.getStoredLeafNodeCount()).isZero();
  }

  @Test
  void canSkipBranch_shouldSkipBranchWhenInDbAndAddToCache() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    when(db.get(schema.getColumnFinalizedStateMerkleTreeBranches(), root))
        .thenReturn(Optional.of(new CompressedBranchInfo(1, new Bytes32[0])));

    assertThat(store.canSkipBranch(root, 3)).isTrue();
    assertThat(store.getSkippedBranchNodeCount()).isEqualTo(1);
    assertThat(knownBranchCache).contains(root);

    // second call should be served from cache without hitting db again
    assertThat(store.canSkipBranch(root, 3)).isTrue();
    verify(db, times(1)).get(schema.getColumnFinalizedStateMerkleTreeBranches(), root);
  }

  @Test
  void canSkipBranch_shouldSkipBranchWhenNewlyStored() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    store.storeBranchNode(root, 3, 1, new Bytes32[0]);

    assertThat(store.canSkipBranch(root, 3)).isTrue();
    assertThat(store.getSkippedBranchNodeCount()).isEqualTo(1);
    assertThat(store.getStoredBranchNodeCount()).isEqualTo(1);
    assertThat(store.getStoredLeafNodeCount()).isZero();
  }

  @Test
  void storeBranchNode_shouldStoreBranchWhenNotAlreadyStored() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final int depth = 2;
    final Bytes32[] children = {
      dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()
    };

    store.storeBranchNode(root, 5, depth, children);

    verify(transaction)
        .put(
            schema.getColumnFinalizedStateMerkleTreeBranches(),
            root,
            new CompressedBranchInfo(depth, children));
    assertThat(store.getStoredBranchNodeCount()).isEqualTo(1);
    assertThat(store.getStoredBranchRoots()).containsExactlyInAnyOrder(root);
  }

  @Test
  void storeBranchNode_shouldNotStoreBranchWhenAlreadyStored() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final int depth = 2;
    final Bytes32[] children = {
      dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()
    };

    store.storeBranchNode(root, 5, depth, children);
    store.storeBranchNode(root, 5, depth, children);

    verify(transaction, times(1))
        .put(
            schema.getColumnFinalizedStateMerkleTreeBranches(),
            root,
            new CompressedBranchInfo(depth, children));

    assertThat(store.getStoredBranchNodeCount()).isEqualTo(1);
  }

  @Test
  void storeBranchNode_shouldNotStoreBranchWhenAlreadyKnown() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    knownBranchCache.add(root);

    store.storeBranchNode(
        root,
        5,
        2,
        new Bytes32[] {dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()});

    verifyNoInteractions(transaction);
    assertThat(store.getStoredBranchNodeCount()).isZero();
  }

  @Test
  void storeBranchNode_shouldRecordMultipleStoredBranchRoots() {
    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();
    final Bytes32 root3 = dataStructureUtil.randomBytes32();

    store.storeBranchNode(
        root1,
        5,
        2,
        new Bytes32[] {dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()});

    store.storeBranchNode(root2, 6, 3, new Bytes32[] {dataStructureUtil.randomBytes32()});

    store.storeBranchNode(root3, 19, 4, new Bytes32[0]);

    assertThat(store.getStoredBranchNodeCount()).isEqualTo(3);
    assertThat(store.getStoredBranchRoots()).containsExactlyInAnyOrder(root1, root2, root3);
  }
}
