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

package tech.pegasys.teku.spec.datastructures.hashtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class HashTreeTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void build_singleChain() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> descendants =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);
    final List<SignedBeaconBlock> chain = new ArrayList<>();
    chain.add(baseBlock);
    chain.addAll(descendants);

    final HashTree blockTree =
        HashTree.builder().rootHash(baseBlock.getRoot()).block(baseBlock).blocks(chain).build();

    validateTreeRepresentsChain(blockTree, chain);
  }

  @Test
  public void build_ignoreRandomBlock() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> descendants =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);
    final SignedBeaconBlock randomBlock = dataStructureUtil.randomSignedBeaconBlock(2);

    final List<SignedBeaconBlock> chain = new ArrayList<>();
    chain.add(baseBlock);
    chain.addAll(descendants);

    final HashTree blockTree =
        HashTree.builder().rootHash(baseBlock.getRoot()).blocks(chain).block(randomBlock).build();

    // Only valid blocks should be available
    validateTreeRepresentsChain(blockTree, chain);
  }

  @Test
  public void build_withForks() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> descendantsA =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 5);
    final List<SignedBeaconBlock> descendantsB =
        dataStructureUtil.randomSignedBeaconBlockSequence(baseBlock, 3);

    final List<SignedBeaconBlock> chainA = new ArrayList<>();
    chainA.add(baseBlock);
    chainA.addAll(descendantsA);

    final List<SignedBeaconBlock> chainB = new ArrayList<>();
    chainB.add(baseBlock);
    chainB.addAll(descendantsB);

    final HashTree tree =
        HashTree.builder().rootHash(baseBlock.getRoot()).blocks(chainA).blocks(chainB).build();

    validateTreeRepresentsChains(tree, List.of(chainA, chainB));

    // Check child counts
    validateChildCountsForChain(tree, descendantsA);
    validateChildCountsForChain(tree, descendantsB);
    assertThat(tree.countChildren(baseBlock.getRoot())).isEqualTo(2);
  }

  @Test
  public void build_withInvalidFork() {
    final SignedBeaconBlock genesis = dataStructureUtil.randomSignedBeaconBlock(0);
    final List<SignedBeaconBlock> chain =
        dataStructureUtil.randomSignedBeaconBlockSequence(genesis, 8);
    final SignedBeaconBlock baseBlock = chain.get(0);

    final List<SignedBeaconBlock> invalidFork =
        dataStructureUtil.randomSignedBeaconBlockSequence(genesis, 3);

    final HashTree blockTree =
        HashTree.builder().rootHash(baseBlock.getRoot()).blocks(chain).blocks(invalidFork).build();

    validateTreeRepresentsChain(blockTree, chain);
  }

  @Test
  public void build_empty() {
    final SignedBeaconBlock baseBlock = dataStructureUtil.randomSignedBeaconBlock(0);

    final HashTree blockTree =
        HashTree.builder().rootHash(baseBlock.getRoot()).block(baseBlock).build();

    assertThat(blockTree.size()).isEqualTo(1);
    assertThat(blockTree.contains(baseBlock.getRoot())).isTrue();
  }

  @Test
  public void preOrderStream() {
    final HashTree blockTree =
        HashTree.builder()
            .rootHash(Bytes32.fromHexString("0x00"))
            .childAndParentRoots(Bytes32.fromHexString("0x0000"), Bytes32.fromHexString("0x9999"))
            // Create branch A of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001A"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002A"), Bytes32.fromHexString("0x001A"))
            .childAndParentRoots(Bytes32.fromHexString("0x003A"), Bytes32.fromHexString("0x002A"))
            // Create branch B of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001B"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002B"), Bytes32.fromHexString("0x001B"))
            .childAndParentRoots(Bytes32.fromHexString("0x003B"), Bytes32.fromHexString("0x002B"))
            // Create branch C of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001C"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002C"), Bytes32.fromHexString("0x001C"))
            .childAndParentRoots(Bytes32.fromHexString("0x003C"), Bytes32.fromHexString("0x002C"))
            .build();

    final List<Bytes32> ordered = blockTree.preOrderStream().collect(Collectors.toList());
    assertThat(ordered)
        .containsExactly(
            Bytes32.fromHexString("0x0000"),
            Bytes32.fromHexString("0x001C"),
            Bytes32.fromHexString("0x002C"),
            Bytes32.fromHexString("0x003C"),
            Bytes32.fromHexString("0x001B"),
            Bytes32.fromHexString("0x002B"),
            Bytes32.fromHexString("0x003B"),
            Bytes32.fromHexString("0x001A"),
            Bytes32.fromHexString("0x002A"),
            Bytes32.fromHexString("0x003A"));
  }

  @Test
  public void breadthFirstStream() {
    final HashTree blockTree =
        HashTree.builder()
            .rootHash(Bytes32.fromHexString("0x00"))
            .childAndParentRoots(Bytes32.fromHexString("0x0000"), Bytes32.fromHexString("0x9999"))
            // Create branch A of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001A"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002A"), Bytes32.fromHexString("0x001A"))
            .childAndParentRoots(Bytes32.fromHexString("0x003A"), Bytes32.fromHexString("0x002A"))
            // Create branch B of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001B"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002B"), Bytes32.fromHexString("0x001B"))
            .childAndParentRoots(Bytes32.fromHexString("0x003B"), Bytes32.fromHexString("0x002B"))
            // Create branch C of length 3
            .childAndParentRoots(Bytes32.fromHexString("0x001C"), Bytes32.fromHexString("0x0000"))
            .childAndParentRoots(Bytes32.fromHexString("0x002C"), Bytes32.fromHexString("0x001C"))
            .childAndParentRoots(Bytes32.fromHexString("0x003C"), Bytes32.fromHexString("0x002C"))
            .build();

    final List<Bytes32> ordered = blockTree.breadthFirstStream().collect(Collectors.toList());
    assertThat(ordered)
        .containsExactly(
            Bytes32.fromHexString("0x0000"),
            Bytes32.fromHexString("0x001A"),
            Bytes32.fromHexString("0x001B"),
            Bytes32.fromHexString("0x001C"),
            Bytes32.fromHexString("0x002A"),
            Bytes32.fromHexString("0x002B"),
            Bytes32.fromHexString("0x002C"),
            Bytes32.fromHexString("0x003A"),
            Bytes32.fromHexString("0x003B"),
            Bytes32.fromHexString("0x003C"));
  }

  private void validateTreeRepresentsChain(
      final HashTree tree, final List<SignedBeaconBlock> chain) {
    validateTreeRepresentsChains(tree, List.of(chain));
  }

  private void validateTreeRepresentsChains(
      final HashTree tree, final List<List<SignedBeaconBlock>> chains) {
    final Set<SignedBeaconBlock> allBlocks =
        chains.stream().flatMap(List::stream).collect(Collectors.toSet());
    assertThat(tree.size()).isEqualTo(allBlocks.size());

    // Check streaming
    List<Bytes32> expectedHashes =
        allBlocks.stream().map(SignedBeaconBlock::getRoot).collect(Collectors.toList());
    List<Bytes32> actualHashes = tree.preOrderStream().collect(Collectors.toList());
    assertThat(actualHashes).containsExactlyInAnyOrderElementsOf(expectedHashes);

    for (List<SignedBeaconBlock> chain : chains) {
      SignedBeaconBlock lastBlock = chain.getLast();

      // All blocks should be available
      for (SignedBeaconBlock block : chain) {
        assertThat(tree.contains(block.getRoot())).isTrue();
        assertThat(tree.getParent(block.getRoot())).contains(block.getParentRoot());
      }

      // Validate children for simple case
      if (chains.size() == 1) {
        validateChildCountsForChain(tree, chain);
      }

      // Check chain collection
      List<Bytes32> chainHashes =
          chain.stream().map(SignedBeaconBlock::getRoot).collect(Collectors.toList());
      List<Bytes32> result = tree.collectChainRoots(lastBlock.getRoot(), __ -> true);
      assertThat(result).isEqualTo(chainHashes);
    }
  }

  private void validateChildCountsForChain(
      final HashTree tree, final List<SignedBeaconBlock> chain) {
    final SignedBeaconBlock lastBlock = chain.getLast();
    for (int i = 0; i < chain.size() - 1; i++) {
      SignedBeaconBlock block = chain.get(i);
      assertThat(tree.countChildren(block.getRoot())).isEqualTo(1);
    }
    assertThat(tree.countChildren(lastBlock.getRoot())).isEqualTo(0);
  }
}
