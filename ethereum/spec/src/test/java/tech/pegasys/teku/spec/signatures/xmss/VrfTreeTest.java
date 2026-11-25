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
 * Checks {@link VrfTree} against the PQ spec's compute_vrf_secret, compute_vrf_leaf and
 * compute_vrf_node (consensus-specs {@code pq} at 4c296784, vectors from its pyspec). A full-depth
 * tree takes about 2^32 hashes, so the vectors use the subtree of depth 10: its root is {@code
 * compute_vrf_node(seed, 10, 0)} and a proof's branch is {@code compute_vrf_node(seed, height,
 * (slot >> height) ^ 1)}, as in compute_vrf_proof.
 */
class VrfTreeTest {

  private static final int DEPTH = 10;

  private record SpecVector(
      Bytes32 seed,
      Map<Long, Bytes32> secrets,
      Map<Long, Bytes32> leaves,
      Bytes32 root,
      Map<Long, List<Bytes32>> branches) {}

  private static final List<SpecVector> SPEC_VECTORS =
      List.of(
          new SpecVector(
              Bytes32.fromHexString(
                  "0x000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0x568e22331e25e613a84a6a86df3f442e2f7f994ccce0b746088fb737c2bc0e20")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0x71ecadaa4a61d7a241550f80653db3818d48e04f96258f0b7befb7084665458b")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0x900ece0f3aa74a193d56767ca3857dfb2676dc0789630e8f555d2018511f69b6")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0x7d65715bc75677b7ae70b0e3a287a883b6424dbdc7fe12476e14a2a28e5f4a72"))),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0x7858a2fe40f519a1db8508fba150a5d89a2f8e6e5c784eb6b5c36728a4364af2")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0x964cac3f9598d16088c9216df1d0a343d9c4439cd365e0008cb81ea8d55210b9")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0xfc1efdcc8c9ee04c7e3547c8b4fc7094541bd87bfa7058e7264c5dec562772d4")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0x36f4fc614f6b2dd139d9b0d901efc91766883657e7f3d9669f6a2478d3b4b8e7"))),
              Bytes32.fromHexString(
                  "0xe2fd22f54650f9bb431288d5632b94b406024a411db4fef773eacec0fae253bb"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x964cac3f9598d16088c9216df1d0a343d9c4439cd365e0008cb81ea8d55210b9"),
                          Bytes32.fromHexString(
                              "0x7853952d91dcac4ca697973efa535487d1c55baff7cc179812ff89bd7fea45c4"),
                          Bytes32.fromHexString(
                              "0x0f24ba17df328536f23e15d16d4909992479dceb871e158fb059082d56375d23"),
                          Bytes32.fromHexString(
                              "0xc693a5be264b4ab755253d9eacf4ae9d42c23f4cf37dfa11511e5bed838e5d0c"),
                          Bytes32.fromHexString(
                              "0x1b6b2b152b32fc6197001bf4efa2b44465e8b5a6512d6612f3d4c6b2367bfd21"),
                          Bytes32.fromHexString(
                              "0x809a67f32686ebbffc4a7898f60b96f0e6c1ad0d9892519b88181b787364e66d"),
                          Bytes32.fromHexString(
                              "0x5f45b68d2daeb24de6dc1c7fadf1e51ac2e1d5d3ef2dbfd954774cae78fba3a1"),
                          Bytes32.fromHexString(
                              "0xaf169bd1554c0553bf4b4a542c1f481395d18edb6bfa4088e80a30a5e99b4f23"),
                          Bytes32.fromHexString(
                              "0x2186e0a3eb692c8d4a4d950ca35f1a842abd80be4efb3592c968154d99764d35"),
                          Bytes32.fromHexString(
                              "0xe404cdf1ac902bfa37f1860004306db1c5d5ec5c7cdb6fa0cf9be268d8f513fe"))),
                  Map.entry(
                      677L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x06e7c40503288e036780ae37a455264fd950352d604ae0679a0b38507ad6ab57"),
                          Bytes32.fromHexString(
                              "0xeffe054fb502e85af99f167a8539b536dbba23c875677907cac29e03d390a97a"),
                          Bytes32.fromHexString(
                              "0x3404fe91f53129fbbf769b98dd81110a797832d600376cce065fac26da6cd009"),
                          Bytes32.fromHexString(
                              "0x6a31768b22476bf22590adc135176d54b3254c5e6755922ba5e5d9d1de4d1460"),
                          Bytes32.fromHexString(
                              "0xbf774d2c9f93623e648bb72f3d2c6ba2c41ad4ad1a1f7a601e89b886656c6343"),
                          Bytes32.fromHexString(
                              "0xf35baf1a351d7caf6d59853d5883fe1969ef963ee5ab277e86b7c7e08382698c"),
                          Bytes32.fromHexString(
                              "0x1b647a406c59dae0530723a7ad21a49b2b5f1bcb588af57907fb600233e028bc"),
                          Bytes32.fromHexString(
                              "0x42788a037dc1a4394325c3039e858d9a6e94ac2ec1b5075655433d4aff9fcc2e"),
                          Bytes32.fromHexString(
                              "0xecbbbf5cb3fe9dbabb4a9123e2f854476cd1f856cf04e9950ea8710650242020"),
                          Bytes32.fromHexString(
                              "0x95d705e38a7ed15b32e1996c19964853832665dace1e88136fb12c87b043a60d"))),
                  Map.entry(
                      1023L,
                      List.of(
                          Bytes32.fromHexString(
                              "0x80bb787df3bd2a1f78fcf368b958c52799667f52b651fa1d5137586ef4c562bd"),
                          Bytes32.fromHexString(
                              "0xab0775bcd1fabb6b108fa8a2ef92f9396aaa7a55178efcaad3c3455d4f8c87f6"),
                          Bytes32.fromHexString(
                              "0xba586562175ef9d42812f7b126809bbb8cae4de713ebefe474263e37f783734d"),
                          Bytes32.fromHexString(
                              "0x402371a3a2acd813e7c23f8ba9477db57a16ed0055c1bfb645265fa356678f32"),
                          Bytes32.fromHexString(
                              "0x9993392f0f7cf2613eb5db132ed9ed58eb076e1ddc4e0bc75fbba90fd07292b7"),
                          Bytes32.fromHexString(
                              "0x19024baed117475a44cc9a9dd77df7c6af743d7b7aa5b78f41beaf1a7b9c86c1"),
                          Bytes32.fromHexString(
                              "0x100102d71ab0fea105c714d1ef0a1ab07793c0438de73a85748c7fb79faea8ba"),
                          Bytes32.fromHexString(
                              "0x1063ad66859b21fd62396c7da082f86af96e193ea98ccdf15a3ad73213512ab7"),
                          Bytes32.fromHexString(
                              "0x0ab42ba366174f23db6f738c348964c8c8fdfdcc14728d5ac482210c48ca1027"),
                          Bytes32.fromHexString(
                              "0x95d705e38a7ed15b32e1996c19964853832665dace1e88136fb12c87b043a60d"))))),
          new SpecVector(
              Bytes32.fromHexString(
                  "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0xeca854d94b26aa0d732c3e32144f8aa2784519e163a340ceaca64a0f7784c7c0")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0xd6ff3155b4109d45bc3e664f94de7c99e0803c176ffaa1b9697301ae51eb5516")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0x82e00872d200ff1acf5828614e0c5b3c3f8e2f232c75bab1f76b847bc73e368f")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0x3c18054cf94cbfffc15e81b5f7c29bd191ad56013bf86299e390d628bae20400"))),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      Bytes32.fromHexString(
                          "0xf40eeb98d309745091eed259ee0e7b40133c02addfcefcbee07fda3a4844914f")),
                  Map.entry(
                      1L,
                      Bytes32.fromHexString(
                          "0xca9323f843116bf68c95ce027d758964a741358091b1d90e074cdc62763aaf51")),
                  Map.entry(
                      5L,
                      Bytes32.fromHexString(
                          "0xb7c419a0ced99be9711716fdd9e81c42ce3e602c7d2cb627f12ad081e92e4c67")),
                  Map.entry(
                      2147483647L,
                      Bytes32.fromHexString(
                          "0x7ad190a441cda3a29178ff078e305e70f5dadfc582047433a05eb2ac5c848751"))),
              Bytes32.fromHexString(
                  "0x07a99bcadd1d3b56b3afbc8837aac41914b11db6c8b61654403a62c725e56e92"),
              Map.ofEntries(
                  Map.entry(
                      0L,
                      List.of(
                          Bytes32.fromHexString(
                              "0xca9323f843116bf68c95ce027d758964a741358091b1d90e074cdc62763aaf51"),
                          Bytes32.fromHexString(
                              "0x3fb34a88f9d4b9b7e2a444aa0b08122533fa0f3bb43cd31d4b58f8b4fae3a4ca"),
                          Bytes32.fromHexString(
                              "0xe8d38643fc5dd5cfc212d78f6d73ee2468c0c3d09b75301d016bf066618009ec"),
                          Bytes32.fromHexString(
                              "0xefe9da9053be181ebbb027678e0b2a019597aa20958fc66fee8f00dfe9f3863d"),
                          Bytes32.fromHexString(
                              "0x3ffe5d41567bee2c486454a0d13ce47f03221d64cc8d87b0b7cb0be20a7d72fc"),
                          Bytes32.fromHexString(
                              "0x17a7ef5ade3f7be2c4d2b665d71b53239cc8851575365577d1b7e4306dfb323d"),
                          Bytes32.fromHexString(
                              "0x59dae98d986f30afe38b1d578fc7ab400a241db54cae55bf1aa75dda7f4d2b59"),
                          Bytes32.fromHexString(
                              "0x6838a7889c882c9608e539af1b42a28a4bf24d0d4886a2169da696d5fc9d11b2"),
                          Bytes32.fromHexString(
                              "0x49990ab3c6555eaa25463da33acdd105c64b500a5700b46f241f1b95a46c5d10"),
                          Bytes32.fromHexString(
                              "0xbb9f85183b65e1a1f1f75aacd2919fb39e7249cd468eb39146b2e71517c090ac"))),
                  Map.entry(
                      677L,
                      List.of(
                          Bytes32.fromHexString(
                              "0xde6dc1ca04c0c6706f925796029e90b1c4c96b28cdb11b84e566b03204086df7"),
                          Bytes32.fromHexString(
                              "0xd7c258221568c37478dbd6370f30e00ce6d95e01be4215133545ddbab013dd5e"),
                          Bytes32.fromHexString(
                              "0xc1b878ae03ac15b911efe4b56be41eb6f03fbd7d21ce4e123ab38be83753b28b"),
                          Bytes32.fromHexString(
                              "0xd92ff6250150838775a5deb7b49fd1b62c058986b9884fb7b4d75ba9f0d01d18"),
                          Bytes32.fromHexString(
                              "0x1c5f6109ce917df3d69fc29b882c2dbacfa99fab7f0f594b42aea02100707a96"),
                          Bytes32.fromHexString(
                              "0xa2484f989e285c4a12605f24710eed61e8f52163fc99be8b9bedf6bf6a7d1230"),
                          Bytes32.fromHexString(
                              "0x5a12916916f7f2ef92d30d2d01e77ae74cba9118c1e0e93c1d99539c6f9b8e1f"),
                          Bytes32.fromHexString(
                              "0x15b041d5ce23c116014144e70ba5cf6fd34ecca27b6b52a83d35ad451aa6a829"),
                          Bytes32.fromHexString(
                              "0x62951cbe78e3eb23d16c8b54bc2e0aafbb0bbd7d87b56656973ba8af1bfdfeac"),
                          Bytes32.fromHexString(
                              "0x609b052175cc1a057e9c5259a7ce5727a09bb88575c40b38770c5beafe8eb475"))),
                  Map.entry(
                      1023L,
                      List.of(
                          Bytes32.fromHexString(
                              "0xb12e9e2af2651a8ef22752024db6ac4c50f2535ba8fe227c361861cb939ab80e"),
                          Bytes32.fromHexString(
                              "0x2c91e4b38737846dd12bd8009a0f995f358c6dc53ce695f66c13fc70c55d1e77"),
                          Bytes32.fromHexString(
                              "0x76e7abc34e78937a4f963b2081ff0d179384a1125fb1503736145f28fc7e549e"),
                          Bytes32.fromHexString(
                              "0x78e419af5c117e751360ced5a57997f2fcd09bdd5735940af7706a6325069964"),
                          Bytes32.fromHexString(
                              "0x1d806811b5fa7c0a1cc393ea02e733c17301f4099a637d6872fa6d15a1e4b4ae"),
                          Bytes32.fromHexString(
                              "0xd4e7c6fd3de3930b990652822663d1a8038c812c5a77ea5fc0bc380fe1d49dba"),
                          Bytes32.fromHexString(
                              "0x36433b21f2fb04d27db7b5572f2fabc853076bf23869f89bf1b2fb3cc1fbd4d5"),
                          Bytes32.fromHexString(
                              "0x99b9836958a5e30aa6cc5051095a295bfac9e81f93f8e820322186321ca56f22"),
                          Bytes32.fromHexString(
                              "0x103201672016caa394e0af8ec3aca5606f69073de9d511cb3b3546c5cbd79977"),
                          Bytes32.fromHexString(
                              "0x609b052175cc1a057e9c5259a7ce5727a09bb88575c40b38770c5beafe8eb475"))))));

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
  void computeSecret_matchesSpec(final SpecVector vector) {
    vector
        .secrets()
        .forEach(
            (index, secret) ->
                assertThat(VrfTree.computeSecret(vector.seed(), UInt64.valueOf(index)))
                    .isEqualTo(secret));
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void computeLeafOfSecret_matchesSpec(final SpecVector vector) {
    vector
        .secrets()
        .forEach(
            (index, secret) ->
                assertThat(VrfTree.computeLeafOfSecret(secret, UInt64.valueOf(index)))
                    .isEqualTo(vector.leaves().get(index)));
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
              // compute_vrf_proof reveals the secret, not the leaf
              expected.add(VrfTree.computeSecret(vector.seed(), UInt64.valueOf(slot)));
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
  void computeRootFromProof_opensOnlyTheProofsSlot() {
    final SpecVector vector = SPEC_VECTORS.getFirst();
    final VrfTree tree = VrfTree.derive(vector.seed(), DEPTH, 4);
    final List<Bytes32> proof = tree.computeProof(UInt64.valueOf(677));

    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(677))).isEqualTo(vector.root());
    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(676)))
        .isNotEqualTo(vector.root());
    // The leaf commits to the whole slot, so a slot with the same low bits doesn't open
    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(677 + (1L << DEPTH))))
        .isNotEqualTo(vector.root());
  }

  @Test
  void computeRootFromProof_doesNotOpenWithTheLeafInPlaceOfTheSecret() {
    final SpecVector vector = SPEC_VECTORS.getFirst();
    final VrfTree tree = VrfTree.derive(vector.seed(), DEPTH, 4);
    final List<Bytes32> proof = new ArrayList<>(tree.computeProof(UInt64.ZERO));
    proof.set(0, vector.leaves().get(0L));

    assertThat(VrfTree.computeRootFromProof(proof, UInt64.ZERO)).isNotEqualTo(vector.root());
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
