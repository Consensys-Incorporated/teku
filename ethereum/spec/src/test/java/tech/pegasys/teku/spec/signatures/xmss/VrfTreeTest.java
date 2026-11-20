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

package tech.pegasys.teku.spec.signatures.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Checks {@link VrfTree} against the PQ spec's compute_vrf_leaf and compute_vrf_node
 * (consensus-specs {@code pq} at 2f43bc0f9). A full-depth tree takes about 2^32 hashes, so the
 * vectors use the subtree of depth 10: its root is {@code compute_vrf_node(seed, 10, 0)} and a
 * proof's branch is {@code compute_vrf_node(seed, height, (slot >> height) ^ 1)}, as in
 * compute_vrf_proof.
 */
class VrfTreeTest {

  private static final int DEPTH = 10;

  private record SpecVector(
      Bytes32 seed, Map<Long, Bytes32> leaves, Bytes32 root, Map<Long, List<Bytes32>> branches) {}

  private static final List<SpecVector> SPEC_VECTORS =
      List.of(
          new SpecVector(
              Bytes32.fromHexString(
                  "0x000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0x8ecde30af40ec9719a0dd0f9e5732ce09bab3ecf290d7a67c4bbbf294ecec649")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0x591f4c9278941277555262ee67b5bcd431fcc91578600a373f53bc2409b2537d")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0x4155b5690790041ca6f793035dd55e1abd4f030c4772dfc716091cd76f05fbfb")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0xff06f282e2461dee3ef25e9cfb360da5206948ab3a6aebe660de10ff6ede3c05"))),
              Bytes32.fromHexString(
                  "0x93f573cb3a92ee26ddece3ea0e2898c8baa176a59e3e5465e9cddf694842b1f0"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x591f4c9278941277555262ee67b5bcd431fcc91578600a373f53bc2409b2537d"),
                          Bytes32.fromHexString(
                              "0x535776a70e604ab96f796706ef2b733e24d33b9808046515f9982953caa9eb3c"),
                          Bytes32.fromHexString(
                              "0x1fb02ca84455ba3d518265c640e1c106ba265d0853e3453da83f91134171840b"),
                          Bytes32.fromHexString(
                              "0xba59f05a5833386d0946ce134d41bc7153a2ef288246f2e509c4db2c38af64d2"),
                          Bytes32.fromHexString(
                              "0x23da5c7c915cdc2f581ab5820ab7ccfd980a9020eb829e5881188310b673a7dc"),
                          Bytes32.fromHexString(
                              "0x8eddf5d0a830fbfada8dfa3f9d457733fce2e54193efc90670a539582048cebe"),
                          Bytes32.fromHexString(
                              "0x849b6671b57726f865677299cd35fef729192869796a946bbee12aa201c5a17f"),
                          Bytes32.fromHexString(
                              "0x5c48df5b99ec13e70dee41de58e3adb86c2c0b45228b1990c35ab78673ea8ea4"),
                          Bytes32.fromHexString(
                              "0x65159403f9d9d2182a5423b9db3d509d6b129b346f8d791106bf11112238c45c"),
                          Bytes32.fromHexString(
                              "0x70e1a3e8566cf22fff9646157c90f75dd802f33d5eca3ad8f7f496fc092a9d79"))),
                  Map.entry(
                      677L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x7f9ce879cdd04cf53c6c47281aeb1452a72875d3561150c757e00791c4ce3634"),
                          Bytes32.fromHexString(
                              "0x7d67de6ef85c6bb8fa1922f0acfa38c2b72161b31d7bb32055593b7617ab413e"),
                          Bytes32.fromHexString(
                              "0xcfc0f76892c299ad4e9bc94fcf780f2a9ff03e062df6d1d05d0a1e5abf9e90a4"),
                          Bytes32.fromHexString(
                              "0x8e30bc361b4a092df1c9346b603e1331428cdb6c31f802e7b2cd0c6ccc880811"),
                          Bytes32.fromHexString(
                              "0x277c4b0d91b59bfd8f345940dd4a60a35faed7ee7ca0ed4c75af5b3c6e81c4e7"),
                          Bytes32.fromHexString(
                              "0xa1072fb1b333f5509b8d0b045558ca2bb6cb799fd3326fb1d567e7976c5aec76"),
                          Bytes32.fromHexString(
                              "0x046fa34d863d43c3745791213add62020cae49602cd9d7acc2189439d459d0af"),
                          Bytes32.fromHexString(
                              "0x67a99fcb210f6e02fdfcbfb23d97c240179cfd11d6d98cef447624739a906b89"),
                          Bytes32.fromHexString(
                              "0xc70c303f3c4c8f11512b8765257bf69fd376e1d61527f520a37cade48b7b0b25"),
                          Bytes32.fromHexString(
                              "0x89fc9759a9278c5ed66aa95528d108dd63d92e42669ebb37dd1bb0329e6f19a0"))),
                  Map.entry(
                      1023L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x8d9458c85a912149d92049f69fd3a796a09ea0fded69ce1e73b68f1e91e636e6"),
                          Bytes32.fromHexString(
                              "0x0be3f9a0a32058ab0054d570cbd534921c8c5edb63d1d117db3b543177c56829"),
                          Bytes32.fromHexString(
                              "0xeafd65af765f432fa5742f6e737a69ca44b0340d3ab26e928767a2f33dce018c"),
                          Bytes32.fromHexString(
                              "0x08bf56cec1c8fda25b83ae55e5ce49cc40a1c034582ab8b8bebe9353d79ee864"),
                          Bytes32.fromHexString(
                              "0x61d0b7fae1667c3816ef20b214aba8a21846b1ae5c401fb24548ca061e49b00c"),
                          Bytes32.fromHexString(
                              "0x0d1fa9ddd936caf91df8fb8a0d23b7718a97fd70d690540adec41d6a4e3b1ad5"),
                          Bytes32.fromHexString(
                              "0x3ba7e71265063af5cb488843e2b8302b0a4cefaabbf9ce7c8204c4fe63b853bb"),
                          Bytes32.fromHexString(
                              "0xf3a81824aec698b9c9125a39058dc987d023d7592c46db8c328f10ea8aa92736"),
                          Bytes32.fromHexString(
                              "0x7d2d387806b9136127c586fa15fb19daff38d836f630381412b0337fb6134114"),
                          Bytes32.fromHexString(
                              "0x89fc9759a9278c5ed66aa95528d108dd63d92e42669ebb37dd1bb0329e6f19a0"))))),
          new SpecVector(
              Bytes32.fromHexString(
                  "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0x7d34f2585f03f62be1390229faf12c46d719090c71097cd92f92be4bab3c21ed")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0xdbddcef39ae95f8cb1918276dc1f8adcb3ed113517ceb0e50300d54a6187f634")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0x59e3852676ed8a1e69b9764abb43a2a596e28e1607d2326c636eede7e304cc33")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0x0803d773d666fe156c758676e83beb09dbdf0034d853f273a4777225fde1cbad"))),
              Bytes32.fromHexString(
                  "0xbaa115bfd56e385b0687c7a88643b0677214a4e6619264d51b869427d5d71d7d"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      List.of(
                          Bytes32.fromHexString(
                              "0xdbddcef39ae95f8cb1918276dc1f8adcb3ed113517ceb0e50300d54a6187f634"),
                          Bytes32.fromHexString(
                              "0x97b2b2c56fcbf53d4bffca9dffad29679f06e0b2729b4290017a2431fc85be0b"),
                          Bytes32.fromHexString(
                              "0x880f2e451c080d2866c7e9f7e7329b9210c58ca4336c2b01f2ea46fe032a8765"),
                          Bytes32.fromHexString(
                              "0xf17c653622cc10ea3b1c382ba92e65695b7e3848b0651ceb269a2d99ef31bfc5"),
                          Bytes32.fromHexString(
                              "0xec6d9f54118b8337a49cc8faf14d530f2fdebbf524ec1947c0bb98fcd00bd6b1"),
                          Bytes32.fromHexString(
                              "0xc5a8547d55ca68e95389fc92d2c180e3b63d71cbe842fcff3b5fd8a5df5b9e51"),
                          Bytes32.fromHexString(
                              "0x60df9284d7f6c9cbfa3590c8f05ea10ecb119e47ff12cacef3557aed9b05b6f0"),
                          Bytes32.fromHexString(
                              "0x712ea48851c95777b4be579b8eb5dd9e48fe6991d64ce3439f3e540a1e8af8f8"),
                          Bytes32.fromHexString(
                              "0x48831a72bf52a1c8ecc999739b7dc865cfb7c834d3e8ea85b0b6aadfc7928f7b"),
                          Bytes32.fromHexString(
                              "0x90af274db217ef805efc1c10288dbdc187f505a092ae043b063f58a17b24db26"))),
                  Map.entry(
                      677L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x07008a74b26ab4a7ba37d4523100cb1b25fa770d9197f389fedcb48f443f5000"),
                          Bytes32.fromHexString(
                              "0xa5f864cf0ab818fa16497d87b39cf3548abfd4cb8f2673fd49704a00a400828e"),
                          Bytes32.fromHexString(
                              "0x113e7b347ab03eceeca1b102e9cb3e9e2b5ef665b204e029ad22b274465130e6"),
                          Bytes32.fromHexString(
                              "0x0b698dfbad9913287ce3fa615a530dce4ee5bb88026dedaa50ea834a679a99bf"),
                          Bytes32.fromHexString(
                              "0x91b32594017bd9998e9ed8d01470843b034be9c0b0795e2d75afed46ef133ea9"),
                          Bytes32.fromHexString(
                              "0x5c22b188b8e84eb231cf862589ccff2f1a21c6fd627e73765c089f5719d69e60"),
                          Bytes32.fromHexString(
                              "0x04a49314c943ad8c927d405713a85c1722cbea4f6f77299c5b9262bd77425a15"),
                          Bytes32.fromHexString(
                              "0xedc0ea3881cd7d7091e0e0390ad6c922e77e012106b261421130636a7717361f"),
                          Bytes32.fromHexString(
                              "0xb569762fe051007d5124f2c049801761ff232355cdcbc52eda775f0e2ad25ced"),
                          Bytes32.fromHexString(
                              "0x9cd7ddf6ef6d575c3d66550bc36b757a74989152280a9c540a06f00dc0ab547a"))),
                  Map.entry(
                      1023L,
                      List.of(
                          Bytes32.fromHexString(
                              "0xc48831e0337060db58da1f3cf00dd0356458df6952b37d6b8dc7f75a57746cfe"),
                          Bytes32.fromHexString(
                              "0x3031e9e392615b3ca8b92b0277a637868b49fde66e041c425998126ccbec83de"),
                          Bytes32.fromHexString(
                              "0x535cc5ccc7b9a07d0eff4c32d29c1b63fde4914ce9cfeed7375f9bfbcbb7e907"),
                          Bytes32.fromHexString(
                              "0xf1a6dbd31e64badb99c0fec0348f6da741f09077d568418dee092e1159d6bf9d"),
                          Bytes32.fromHexString(
                              "0x5d8537fc3b4e427a39cd8e5b1f022179d458cd562788c928b981cff7cd1b1bda"),
                          Bytes32.fromHexString(
                              "0x086f66d4693dff793686540a131d5568e56bb465669056c8d39ab36c722843fa"),
                          Bytes32.fromHexString(
                              "0x3d399e2f93d42f4804f4a592e0697de2eaf8a5b7d7b2cf9efb992db4cd4b6584"),
                          Bytes32.fromHexString(
                              "0x6b573dbed6733939b1c7499c787053194d540b9bbfb2ae7aebdbcdb9bf6dfb8a"),
                          Bytes32.fromHexString(
                              "0x5f2b61ced3fd80b05294bae2be53d85df5dd4615097f34d24b20ea11f5885882"),
                          Bytes32.fromHexString(
                              "0x9cd7ddf6ef6d575c3d66550bc36b757a74989152280a9c540a06f00dc0ab547a"))))));

  static Stream<Arguments> vectorsAndCachedHeights() {
    return SPEC_VECTORS.stream()
        .flatMap(
            vector ->
                Stream.of(0, 4, DEPTH).map(cachedHeight -> Arguments.of(vector, cachedHeight)));
  }

  static Stream<SpecVector> vectors() {
    return SPEC_VECTORS.stream();
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void computeLeaf_matchesSpec(final SpecVector vector) {
    vector
        .leaves()
        .forEach(
            (index, leaf) ->
                assertThat(VrfTree.computeLeaf(vector.seed(), UInt64.valueOf(index)))
                    .isEqualTo(leaf));
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void computeNode_matchesSpecRoot(final SpecVector vector) {
    assertThat(VrfTree.computeNode(vector.seed(), DEPTH, 0)).isEqualTo(vector.root());
    // A height-0 node is the leaf itself
    assertThat(VrfTree.computeNode(vector.seed(), 0, 5)).isEqualTo(vector.leaves().get(5L));
  }

  @ParameterizedTest
  @MethodSource("vectorsAndCachedHeights")
  void derive_matchesSpecRootAndProofs(final SpecVector vector, final int cachedHeight) {
    final VrfTree tree = VrfTree.derive(vector.seed(), DEPTH, cachedHeight);

    assertThat(tree.getRoot()).isEqualTo(vector.root());
    vector
        .branches()
        .forEach(
            (slot, branch) -> {
              final List<Bytes32> proof = tree.computeProof(UInt64.valueOf(slot));
              final List<Bytes32> expected = new ArrayList<>();
              expected.add(VrfTree.computeLeaf(vector.seed(), UInt64.valueOf(slot)));
              expected.addAll(branch);
              assertThat(proof).containsExactlyElementsOf(expected);
              assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(slot)))
                  .isEqualTo(vector.root());
            });
  }

  @ParameterizedTest
  @MethodSource("vectorsAndCachedHeights")
  void fromCachedNodes_restoresTheTree(final SpecVector vector, final int cachedHeight) {
    final VrfTree tree = VrfTree.derive(vector.seed(), DEPTH, cachedHeight);
    final Bytes cachedNodes = tree.getCachedNodes();
    assertThat(cachedNodes.size()).isEqualTo((1 << (DEPTH - cachedHeight)) * Bytes32.SIZE);

    final VrfTree restored =
        VrfTree.fromCachedNodes(vector.seed(), DEPTH, cachedHeight, cachedNodes);

    assertThat(restored.getRoot()).isEqualTo(vector.root());
    for (final long slot : vector.branches().keySet()) {
      assertThat(restored.computeProof(UInt64.valueOf(slot)))
          .isEqualTo(tree.computeProof(UInt64.valueOf(slot)));
    }
  }

  @Test
  void computeNode_matchesDevnetGenesisTool() {
    // The pinned vector in ethpandaops/eth-beacon-genesis (pq) validators/vrf_test.go: node 1 at
    // height 18 for the VRF seed of its devnet validator 0
    final Bytes32 seed =
        Bytes32.fromHexString("0x543fc5a4d86e79a434e9741770f7ee17c25bdc31be8d61436d7f9836538b03df");

    assertThat(VrfTree.computeNode(seed, 18, 1))
        .isEqualTo(
            Bytes32.fromHexString(
                "0xa8e932e0dd6ec363635fdb0b30ea60d24cc1a861904c879e236c0e77f9efa562"));
  }

  @Test
  void computeRootFromProof_onlyReadsTheLowSlotBits() {
    final SpecVector vector = SPEC_VECTORS.getFirst();
    final VrfTree tree = VrfTree.derive(vector.seed(), DEPTH, 4);
    final List<Bytes32> proof = tree.computeProof(UInt64.valueOf(677));

    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(677 + (1L << DEPTH))))
        .isEqualTo(vector.root());
    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(676)))
        .isNotEqualTo(vector.root());
  }

  @Test
  void computeProof_rejectsSlotsBeyondTheTree() {
    final VrfTree tree = VrfTree.derive(SPEC_VECTORS.getFirst().seed(), DEPTH, 4);

    assertThatThrownBy(() -> tree.computeProof(UInt64.valueOf(1L << DEPTH)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromCachedNodes_rejectsTheWrongNumberOfNodes() {
    final Bytes32 seed = SPEC_VECTORS.getFirst().seed();

    assertThatThrownBy(
            () -> VrfTree.fromCachedNodes(seed, DEPTH, 4, Bytes.wrap(new byte[Bytes32.SIZE])))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void derive_reportsProgressForEveryCachedNode() {
    final Bytes32 seed = Bytes32.fromHexStringLenient("0x5eed");
    final Set<Integer> reported = ConcurrentHashMap.newKeySet();
    final AtomicInteger calls = new AtomicInteger();

    final VrfTree tree =
        VrfTree.derive(
            seed,
            DEPTH,
            4,
            (derivedNodes, totalNodes) -> {
              assertThat(totalNodes).isEqualTo(1 << (DEPTH - 4));
              reported.add(derivedNodes);
              calls.incrementAndGet();
            });

    assertThat(calls).hasValue(1 << (DEPTH - 4));
    assertThat(reported).hasSize(1 << (DEPTH - 4)).contains(1, 1 << (DEPTH - 4));
    assertThat(tree.getRoot()).isEqualTo(VrfTree.derive(seed, DEPTH, 4).getRoot());
    assertThat(tree.getDepth()).isEqualTo(DEPTH);
    assertThat(tree.getCachedHeight()).isEqualTo(4);
  }
}
