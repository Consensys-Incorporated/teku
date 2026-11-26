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

package tech.pegasys.teku.xmss;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class XmssSigningKeyCacheTest {

  private static final XmssSecretKey SECRET_KEY =
      XmssSecretKey.fromBytes(Bytes32.fromHexString("0x" + "07".repeat(32)));
  private static final UInt64 LEAF_IN_TREE_0 = UInt64.valueOf(40_000);
  private static final UInt64 LEAF_IN_TREE_3 = UInt64.valueOf(3 * (1L << 16) + 5);
  private static final Bytes32 MESSAGE = Bytes32.fromHexString("0x" + "ab".repeat(32));

  // Built once: building a key and a bottom tree takes seconds each
  private static XmssSigningKey builtKey;
  private static Bytes cache;

  @BeforeAll
  static void buildKey() {
    builtKey = Xmss.loadSigningKey(SECRET_KEY);
    builtKey.prepare(LEAF_IN_TREE_0);
    cache = builtKey.cacheToBytes();
  }

  @AfterAll
  static void closeKey() {
    builtKey.close();
  }

  @Test
  void cacheHoldsTheTopTreeAndTheBottomTreesUsed() {
    assertThat(cache.size())
        .isEqualTo(XmssSigningKey.CACHE_TOP_TREE_SIZE + XmssSigningKey.CACHE_BOTTOM_TREE_SIZE);
    assertThat(XmssSigningKey.cachedBottomTrees(cache)).contains(List.of(0));
  }

  @Test
  void keyLoadedFromItsCacheSignsAsTheBuiltKey() {
    try (XmssSigningKey loaded =
        Xmss.loadSigningKey(SECRET_KEY, builtKey.getPublicKey(), cache).orElseThrow()) {
      assertThat(loaded.getPublicKey()).isEqualTo(builtKey.getPublicKey());
      final XmssSignature signature = loaded.sign(LEAF_IN_TREE_0, MESSAGE);
      assertThat(signature).isEqualTo(builtKey.sign(LEAF_IN_TREE_0, MESSAGE));
      assertThat(Xmss.verify(builtKey.getPublicKey(), LEAF_IN_TREE_0, MESSAGE, signature)).isTrue();
    }
  }

  @Test
  void cacheOnlyLoadsForItsPublicKeyAndSeed() {
    final MutableBytes otherRoot = builtKey.getPublicKey().toBytes().mutableCopy();
    otherRoot.set(0, (byte) (otherRoot.get(0) ^ 1));
    assertThat(Xmss.loadSigningKey(SECRET_KEY, XmssPublicKey.fromBytes(otherRoot), cache))
        .isEmpty();
    assertThat(
            Xmss.loadSigningKey(
                XmssSecretKey.fromBytes(Bytes32.fromHexString("0x" + "08".repeat(32))),
                builtKey.getPublicKey(),
                cache))
        .isEmpty();
  }

  @Test
  void malformedCachesDoNotLoad() {
    final XmssPublicKey publicKey = builtKey.getPublicKey();
    assertThat(Xmss.loadSigningKey(SECRET_KEY, publicKey, cache.slice(0, 100))).isEmpty();
    assertThat(Xmss.loadSigningKey(SECRET_KEY, publicKey, Bytes.concatenate(cache, Bytes.of(0))))
        .isEmpty();
    // a bottom tree index out of range
    final Bytes badIndex =
        Bytes.concatenate(
            cache.slice(0, XmssSigningKey.CACHE_TOP_TREE_SIZE),
            Bytes.fromHexString("0x00000100"),
            cache.slice(XmssSigningKey.CACHE_TOP_TREE_SIZE + 4));
    assertThat(XmssSigningKey.cachedBottomTrees(badIndex)).isEmpty();
    assertThat(Xmss.loadSigningKey(SECRET_KEY, publicKey, badIndex)).isEmpty();
  }

  @Test
  void reportsEachNewBottomTreeOnce() {
    try (XmssSigningKey loaded =
        Xmss.loadSigningKey(SECRET_KEY, builtKey.getPublicKey(), cache).orElseThrow()) {
      final AtomicInteger built = new AtomicInteger();
      loaded.onBottomTreeBuilt(built::incrementAndGet);

      // tree 0 came with the cache
      loaded.sign(LEAF_IN_TREE_0, MESSAGE);
      assertThat(built).hasValue(0);

      loaded.prepare(LEAF_IN_TREE_3);
      loaded.sign(LEAF_IN_TREE_3, MESSAGE);
      assertThat(built).hasValue(1);
      assertThat(XmssSigningKey.cachedBottomTrees(loaded.cacheToBytes())).contains(List.of(0, 3));
    }
  }

  @Test
  void cacheKeepsOnlyTheLastBottomTreesUsed() {
    try (XmssSigningKey loaded =
        Xmss.loadSigningKey(SECRET_KEY, builtKey.getPublicKey(), cache).orElseThrow()) {
      // tree 0 came with the cache; four more push it out
      for (long tree = 1; tree <= XmssSigningKey.MAX_CACHED_BOTTOM_TREES; tree++) {
        loaded.prepare(XmssSigningKey.firstLeafOfBottomTree(tree));
      }
      final Bytes trees = loaded.cacheToBytes();
      assertThat(trees.size()).isEqualTo(XmssSigningKey.MAX_CACHE_SIZE);
      assertThat(XmssSigningKey.cachedBottomTrees(trees)).contains(List.of(1, 2, 3, 4));
      try (XmssSigningKey reloaded =
          Xmss.loadSigningKey(SECRET_KEY, builtKey.getPublicKey(), trees).orElseThrow()) {
        assertThat(reloaded.getPublicKey()).isEqualTo(builtKey.getPublicKey());
      }
    }
  }

  @Test
  void publicParameterIsTheEndOfThePublicKey() {
    assertThat(Xmss.publicParameter(SECRET_KEY))
        .isEqualTo(
            builtKey
                .getPublicKey()
                .toBytes()
                .slice(XmssConstants.PUBLIC_KEY_SIZE - Xmss.PUBLIC_PARAMETER_SIZE));
  }

  @Test
  void bottomTreeOfALeaf() {
    assertThat(XmssSigningKey.bottomTreeOf(UInt64.valueOf(65_535))).isZero();
    assertThat(XmssSigningKey.bottomTreeOf(UInt64.valueOf(65_536))).isEqualTo(1);
    assertThat(Optional.of(XmssSigningKey.bottomTreeOf(LEAF_IN_TREE_3))).contains(3L);
  }
}
