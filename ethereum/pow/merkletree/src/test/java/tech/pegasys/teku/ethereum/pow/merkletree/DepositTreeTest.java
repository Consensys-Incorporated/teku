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

package tech.pegasys.teku.ethereum.pow.merkletree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class DepositTreeTest {
  private final Spec spec = TestSpecFactory.createDefault();

  private final DepositTree depositTree = new DepositTree();

  @ParameterizedTest(name = "nonFinalizedDeposits: {0}")
  @ValueSource(ints = {0, 1, 2, 10, 50, 100, 128, 511, 512})
  void shouldGenerateSnapshots(final int nonFinalizedDepositCount) throws Exception {
    final List<DepositTestCase> testCases = loadEipTestCases();
    for (int i = 0; i < testCases.size(); i++) {
      final DepositTestCase testCase = testCases.get(i);
      depositTree.pushLeaf(testCase.depositDataRoot);
      assertThat(depositTree.getDepositCount())
          .isEqualTo(testCase.getEth1Data().getDepositCount().longValue());

      if (i >= nonFinalizedDepositCount) {
        final DepositTestCase finalisingTestCase = testCases.get(i - nonFinalizedDepositCount);
        depositTree.finalize(finalisingTestCase.getEth1Data(), finalisingTestCase.getBlockHeight());
        final Optional<DepositTreeSnapshot> snapshotOptional = depositTree.getSnapshot();
        assertThat(snapshotOptional).contains(finalisingTestCase.getSnapshot());
      }
    }
  }

  @ParameterizedTest(name = "deposits: {0}")
  @ValueSource(ints = {9, 10})
  void partialSnapshotShouldHaveCorrectDepositCount(final int depositsCount) {
    final Bytes32 depositRoot = Bytes32.random();
    final Bytes32 blockHash = Bytes32.random();
    final Function<Integer, Eth1Data> eth1Generator =
        deposits -> new Eth1Data(depositRoot, UInt64.valueOf(deposits), blockHash);

    final List<Bytes32> leafs = new ArrayList<>();
    for (int i = 0; i < depositsCount; ++i) {
      leafs.add(Bytes32.random());
    }
    final DepositTree fullTree = new DepositTree();
    leafs.forEach(fullTree::pushLeaf);
    fullTree.finalize(eth1Generator.apply(depositsCount), UInt64.ZERO);
    final DepositTreeSnapshot fullTreeSnapshot = fullTree.getSnapshot().orElseThrow();

    Optional<DepositTreeSnapshot> maybePreviousSnapshot = Optional.empty();
    for (int finalized = 0; finalized <= depositsCount; ++finalized) {

      final DepositTree customTree = new DepositTree();
      for (int j = 0; j < depositsCount; ++j) {
        customTree.pushLeaf(leafs.get(j));
      }
      customTree.finalize(eth1Generator.apply(finalized), UInt64.ZERO);
      final Optional<DepositTreeSnapshot> customTreeSnapshot = customTree.getSnapshot();

      if (finalized == 0) {
        assertThat(customTreeSnapshot).isEmpty();
      } else {
        assertThat(customTreeSnapshot).isPresent();
        final DepositTreeSnapshot snapshot = customTreeSnapshot.get();

        // not equal to previous finalized
        maybePreviousSnapshot.ifPresent(
            previousSnapshot -> {
              assertThat(snapshot.getDepositRoot()).isNotEqualTo(previousSnapshot.getDepositRoot());
              assertThat(snapshot.getFinalized()).isNotEqualTo(previousSnapshot.getFinalized());
            });
        if (finalized == depositsCount) {
          assertThat(customTreeSnapshot).contains(fullTreeSnapshot);
        } else {
          // not equal to full finalized
          assertThat(snapshot.getDepositCount()).isEqualTo(finalized);
          assertThat(snapshot.getDepositRoot()).isNotEqualTo(fullTreeSnapshot.getDepositRoot());
          assertThat(snapshot.getFinalized()).isNotEqualTo(fullTreeSnapshot.getFinalized());
        }
      }

      maybePreviousSnapshot = customTreeSnapshot;
    }
  }

  @Test
  void shouldRestoreFromSnapshots() throws Exception {
    final List<DepositTestCase> testCases = loadEipTestCases();
    for (DepositTestCase testCase : testCases) {
      final DepositTree tree = DepositTree.fromSnapshot(testCase.snapshot);
      assertThat(tree.getRoot()).isEqualTo(testCase.eth1Data.getDepositRoot());
    }
  }

  @Test
  void shouldFinalizeWithZeroDeposits() {
    final DepositTree tree = new DepositTree();
    // Empty tree root
    assertThat(tree.getRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "d70a234731285c6804c2a4f56711ddb8c82c99740f207854891028af34e27e5e"));
    // Not finalized
    assertThat(tree.getSnapshot()).isEmpty();
    final Eth1Data eth1Data =
        new Eth1Data(Bytes32.fromHexString("0x1234"), UInt64.ZERO, Bytes32.fromHexString("0x5678"));

    assertThatNoException().isThrownBy(() -> tree.finalize(eth1Data, UInt64.ONE));
  }

  @Test
  void shouldGetProofForDeposits() throws Exception {
    final List<DepositTestCase> testCases = loadEipTestCases();
    for (int i = 0; i < testCases.size(); i++) {
      final DepositTestCase testCase = testCases.get(i);
      depositTree.pushLeaf(testCase.depositDataRoot);
      final List<Bytes32> proof = depositTree.getProof(i);
      assertThat(depositTree.getRoot()).isEqualTo(testCase.getEth1Data().getDepositRoot());
      final boolean isValid =
          spec.getGenesisSpec()
              .predicates()
              .isValidMerkleBranch(
                  testCase.depositDataRoot,
                  Deposit.SSZ_SCHEMA.getProofSchema().of(proof),
                  // +1 because the spec doesn't count the root node as part of depth
                  DEPOSIT_CONTRACT_TREE_DEPTH + 1,
                  i,
                  testCase.getEth1Data().getDepositRoot());
      assertThat(isValid)
          .withFailMessage("Generated invalid proof for deposit %s. Proof: %s", i, proof)
          .isTrue();
    }
  }

  @Test
  void shouldGetTreeAtDepositIndex() throws Exception {
    // Apply all the deposits to the tree
    final List<DepositTestCase> testCases = loadEipTestCases();
    for (final DepositTestCase testCase : testCases) {
      depositTree.pushLeaf(testCase.depositDataRoot);
    }

    // Then walk back through the test cases checking we can roll back the tree to the same point
    for (int i = 0; i < testCases.size(); i++) {
      final DepositTestCase testCase = testCases.get(i);
      // +1 because we're getting the deposit tree *after* applying the deposit for this case
      final DepositTree tree = depositTree.getTreeAtDepositIndex(i + 1);
      assertThat(tree.getRoot())
          .describedAs("Tree root after deposit %s", i)
          .isEqualTo(testCase.getEth1Data().getDepositRoot());
    }
  }

  @Test
  void shouldFailOnWrongRoot() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    depositTree.pushLeaf(dataStructureUtil.randomBytes32());
    depositTree.finalize(
        new Eth1Data(
            dataStructureUtil.randomBytes32(), UInt64.ONE, dataStructureUtil.randomBytes32()),
        UInt64.ONE);
    final Optional<DepositTreeSnapshot> snapshot = depositTree.getSnapshot();
    assertThat(snapshot).isNotEmpty();
    final DepositTree restoredTree = DepositTree.fromSnapshot(snapshot.get());
    assertThat(restoredTree.getDepositCount()).isEqualTo(depositTree.getDepositCount());

    final DepositTreeSnapshot brokenSnapshot =
        new DepositTreeSnapshot(
            snapshot.get().getFinalized(),
            dataStructureUtil.randomBytes32(), // change root
            snapshot.get().getDepositCount(),
            snapshot.get().getExecutionBlockHash(),
            snapshot.get().getExecutionBlockHeight());
    assertThatThrownBy(() -> DepositTree.fromSnapshot(brokenSnapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private List<DepositTestCase> loadEipTestCases() throws Exception {
    return JsonUtil.parse(
        new YAMLFactory(),
        DepositTreeTest.class.getResourceAsStream("test_cases.yml"),
        DeserializableTypeDefinition.listOf(TEST_CASE_TYPE));
  }

  private static final DeserializableTypeDefinition<DepositTreeSnapshot>
      DEPOSIT_TREE_SNAPSHOT_TYPE = DepositTreeSnapshot.getJsonTypeDefinition();

  private static final DeserializableTypeDefinition<DepositTestCase> TEST_CASE_TYPE =
      DeserializableTypeDefinition.object(DepositTestCase.class)
          .initializer(DepositTestCase::new)
          .withField(
              "deposit_data",
              DepositData.SSZ_SCHEMA.getJsonTypeDefinition(),
              DepositTestCase::getDepositData,
              DepositTestCase::setDepositData)
          .withField(
              "deposit_data_root",
              CoreTypes.BYTES32_TYPE,
              DepositTestCase::getDepositDataRoot,
              DepositTestCase::setDepositDataRoot)
          .withField(
              "eth1_data",
              Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(),
              DepositTestCase::getEth1Data,
              DepositTestCase::setEth1Data)
          .withField(
              "block_height",
              CoreTypes.UINT64_TYPE,
              DepositTestCase::getBlockHeight,
              DepositTestCase::setBlockHeight)
          .withField(
              "snapshot",
              DEPOSIT_TREE_SNAPSHOT_TYPE,
              DepositTestCase::getSnapshot,
              DepositTestCase::setSnapshot)
          .build();

  private static class DepositTestCase {
    private DepositData depositData;
    private Bytes32 depositDataRoot;
    private Eth1Data eth1Data;
    private UInt64 blockHeight;
    private DepositTreeSnapshot snapshot;

    public DepositData getDepositData() {
      return depositData;
    }

    public void setDepositData(final DepositData depositData) {
      this.depositData = depositData;
    }

    public Bytes32 getDepositDataRoot() {
      return depositDataRoot;
    }

    public void setDepositDataRoot(final Bytes32 depositDataRoot) {
      this.depositDataRoot = depositDataRoot;
    }

    public Eth1Data getEth1Data() {
      return eth1Data;
    }

    public void setEth1Data(final Eth1Data eth1Data) {
      this.eth1Data = eth1Data;
    }

    public DepositTreeSnapshot getSnapshot() {
      return snapshot;
    }

    public void setSnapshot(final DepositTreeSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    public UInt64 getBlockHeight() {
      return blockHeight;
    }

    public void setBlockHeight(final UInt64 blockHeight) {
      this.blockHeight = blockHeight;
    }
  }
}
