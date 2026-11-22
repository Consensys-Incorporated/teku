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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.leanpq.LeanPqException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A leanXMSS key loaded into the native library, ready to sign. Building it from its seed hashes
 * the key's top tree (seconds), and the first signature in each bottom tree of {@value
 * #BOTTOM_TREE_LEAVES} leaves builds that tree (seconds more), so keep one open per key rather than
 * loading it for each signature, and close it when done.
 *
 * <p>The trees hold no secret and can be cached: {@link #cacheToBytes()} saves the top tree and the
 * last {@value #MAX_CACHED_BOTTOM_TREES} bottom trees used, and {@link #fromCache} loads them again
 * in milliseconds, given the key's public key from a trusted source.
 *
 * <p>This class signs whatever leaf it is given. Never sign two different messages at the same
 * leaf: that reveals enough of the key to forge signatures. Callers must record a leaf as used
 * before publishing any signature made with it.
 */
public final class XmssSigningKey implements AutoCloseable {

  /** Leaves per bottom tree. */
  public static final long BOTTOM_TREE_LEAVES = 1L << 16;

  /** Bytes of the top tree in {@link #cacheToBytes()}. */
  public static final int CACHE_TOP_TREE_SIZE = 4096;

  /** Bytes of each bottom tree in {@link #cacheToBytes()}: its index, then its trees. */
  public static final int CACHE_BOTTOM_TREE_SIZE = 5300;

  /** How many recently used bottom trees the native key keeps. */
  public static final int MAX_CACHED_BOTTOM_TREES = 4;

  public static final int MAX_CACHE_SIZE =
      CACHE_TOP_TREE_SIZE + MAX_CACHED_BOTTOM_TREES * CACHE_BOTTOM_TREE_SIZE;

  private final org.ethereum.leanpq.XmssSecretKey key;
  private final XmssPublicKey publicKey;
  // The bottom trees this key has built or loaded, so a newly built one can be reported
  private final Set<Long> bottomTrees = new HashSet<>();
  private volatile Runnable onBottomTreeBuilt = () -> {};

  XmssSigningKey(final XmssSecretKey secretKey) {
    this(org.ethereum.leanpq.XmssSecretKey.fromSeed(secretKey.toBytes().toArray()), List.of());
  }

  private XmssSigningKey(
      final org.ethereum.leanpq.XmssSecretKey key, final List<Integer> cachedBottomTrees) {
    this.key = key;
    this.publicKey = XmssPublicKey.fromBytes(Bytes.wrap(key.publicKey()));
    cachedBottomTrees.forEach(index -> bottomTrees.add(Integer.toUnsignedLong(index)));
  }

  /**
   * Loads a key from the bytes of {@link #cacheToBytes()}, which must hold this seed's trees for
   * {@code publicKey}. Every cached tree is checked against {@code publicKey}, so it must come from
   * a trusted source: the trees only show they belong to whatever public key they hash to.
   *
   * @return empty if the cache is malformed or isn't of this key
   */
  public static Optional<XmssSigningKey> fromCache(
      final XmssSecretKey secretKey, final XmssPublicKey publicKey, final Bytes cache) {
    final Optional<List<Integer>> bottomTrees = cachedBottomTrees(cache);
    if (bottomTrees.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new XmssSigningKey(
              org.ethereum.leanpq.XmssSecretKey.fromCacheBytes(
                  secretKey.toBytes().toArray(),
                  publicKey.toBytes().toArray(),
                  cache.toArrayUnsafe()),
              bottomTrees.get()));
    } catch (final LeanPqException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * The indices of the bottom trees in a cache, sorted, or empty if it isn't a well-formed cache.
   */
  public static Optional<List<Integer>> cachedBottomTrees(final Bytes cache) {
    if (cache.size() < CACHE_TOP_TREE_SIZE
        || cache.size() > MAX_CACHE_SIZE
        || (cache.size() - CACHE_TOP_TREE_SIZE) % CACHE_BOTTOM_TREE_SIZE != 0) {
      return Optional.empty();
    }
    final List<Integer> indices = new ArrayList<>();
    for (int offset = CACHE_TOP_TREE_SIZE;
        offset < cache.size();
        offset += CACHE_BOTTOM_TREE_SIZE) {
      final int index = cache.slice(offset, Integer.BYTES).toInt(ByteOrder.LITTLE_ENDIAN);
      // Bottom tree indices are below 2^16
      if (Integer.compareUnsigned(index, (int) BOTTOM_TREE_LEAVES) >= 0
          || indices.contains(index)) {
        return Optional.empty();
      }
      indices.add(index);
    }
    indices.sort(Integer::compare);
    return Optional.of(indices);
  }

  /** The index of the bottom tree that {@code leafIndex} is in. */
  public static long bottomTreeOf(final UInt64 leafIndex) {
    return leafIndex.longValue() / BOTTOM_TREE_LEAVES;
  }

  public XmssPublicKey getPublicKey() {
    return publicKey;
  }

  /**
   * Runs {@code listener}, on the signing thread, after each signature or preparation that built a
   * bottom tree this key hadn't built or loaded before: the moment to save {@link #cacheToBytes()}.
   */
  public void onBottomTreeBuilt(final Runnable listener) {
    this.onBottomTreeBuilt = listener;
  }

  /** The key's cached trees, which hold no secret. See {@link #fromCache}. */
  public synchronized Bytes cacheToBytes() {
    return Bytes.wrap(key.cacheToBytes());
  }

  /** Precomputes the signing state for {@code leafIndex} so a later {@link #sign} is fast. */
  public void prepare(final UInt64 leafIndex) {
    XmssSignature.encodeLeafIndex(leafIndex);
    final boolean built;
    synchronized (this) {
      key.prepare(leafIndex.longValue());
      built = bottomTrees.add(bottomTreeOf(leafIndex));
    }
    if (built) {
      onBottomTreeBuilt.run();
    }
  }

  public XmssSignature sign(final UInt64 leafIndex, final Bytes32 message) {
    XmssSignature.encodeLeafIndex(leafIndex);
    final XmssSignature signature;
    final boolean built;
    synchronized (this) {
      signature =
          XmssSignature.fromBytes(Bytes.wrap(key.sign(message.toArray(), leafIndex.longValue())));
      built = bottomTrees.add(bottomTreeOf(leafIndex));
    }
    if (built) {
      onBottomTreeBuilt.run();
    }
    return signature;
  }

  @Override
  public synchronized void close() {
    key.close();
  }
}
