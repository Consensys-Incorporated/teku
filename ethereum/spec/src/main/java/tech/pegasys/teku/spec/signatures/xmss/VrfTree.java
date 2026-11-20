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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.PqConstants;

/**
 * A validator's VRF tree (PQ validator.md, "VRF tree"): a Keccak-256 Merkle tree of depth {@link
 * PqConstants#VRF_TREE_DEPTH} with one leaf per slot, derived from a secret 32-byte seed. Its root
 * is the validator's registered VRF root, and the proof for a slot is the validator's RANDAO
 * reveal.
 *
 * <p>Deriving the root hashes every leaf, about 2^32 Keccak-256 calls at full depth, so it is a
 * one-off when the key is created. This class keeps the nodes at {@code cachedHeight} and every
 * node above them; a proof then costs one subtree of 2^{@code cachedHeight} leaves. At the default
 * height of 16 the cache is 2^15 nodes (1 MiB) per validator. {@link #getCachedNodes()} and {@link
 * #fromCachedNodes} persist it so the root is only derived once.
 *
 * <p>The seed is secret: anyone who learns it can compute the validator's future RANDAO reveals.
 */
public final class VrfTree {

  public static final int DEFAULT_CACHED_HEIGHT = 16;

  private final Bytes32 seed;
  private final int depth;
  private final int cachedHeight;
  // upperLayers.get(i) holds the nodes at height cachedHeight + i, up to the root
  private final List<Bytes32[]> upperLayers;

  private VrfTree(
      final Bytes32 seed, final int depth, final int cachedHeight, final Bytes32[] cachedNodes) {
    this.seed = seed;
    this.depth = depth;
    this.cachedHeight = cachedHeight;
    this.upperLayers = buildUpperLayers(cachedNodes);
  }

  /** Derives the full-depth tree for {@code seed}, hashing every leaf in parallel. */
  public static VrfTree derive(final Bytes32 seed) {
    return derive(seed, PqConstants.VRF_TREE_DEPTH, DEFAULT_CACHED_HEIGHT);
  }

  /**
   * Derives the full-depth tree for {@code seed}, reporting progress as each cached node is
   * derived. Deriving hashes about 2^32 leaves, which takes minutes.
   */
  public static VrfTree derive(final Bytes32 seed, final DeriveProgressListener progressListener) {
    return derive(seed, PqConstants.VRF_TREE_DEPTH, DEFAULT_CACHED_HEIGHT, progressListener);
  }

  /** Derives a tree of {@code depth}, caching the nodes at {@code cachedHeight}. */
  public static VrfTree derive(final Bytes32 seed, final int depth, final int cachedHeight) {
    return derive(seed, depth, cachedHeight, DeriveProgressListener.NOOP);
  }

  /**
   * Derives a tree of {@code depth}, caching the nodes at {@code cachedHeight} and reporting
   * progress as each cached node is derived.
   */
  public static VrfTree derive(
      final Bytes32 seed,
      final int depth,
      final int cachedHeight,
      final DeriveProgressListener progressListener) {
    checkShape(depth, cachedHeight);
    final int nodeCount = 1 << (depth - cachedHeight);
    final AtomicInteger derivedCount = new AtomicInteger();
    final Bytes32[] cachedNodes =
        IntStream.range(0, nodeCount)
            .parallel()
            .mapToObj(
                index -> {
                  final Bytes32 node = computeNode(seed, cachedHeight, index);
                  progressListener.onProgress(derivedCount.incrementAndGet(), nodeCount);
                  return node;
                })
            .toArray(Bytes32[]::new);
    return new VrfTree(seed, depth, cachedHeight, cachedNodes);
  }

  /**
   * Restores a full-depth tree from nodes saved with {@link #getCachedNodes()}. The caller should
   * check {@link #getRoot()} against the validator's registered VRF root.
   */
  public static VrfTree fromCachedNodes(final Bytes32 seed, final Bytes cachedNodes) {
    return fromCachedNodes(seed, PqConstants.VRF_TREE_DEPTH, DEFAULT_CACHED_HEIGHT, cachedNodes);
  }

  public static VrfTree fromCachedNodes(
      final Bytes32 seed, final int depth, final int cachedHeight, final Bytes cachedNodes) {
    checkShape(depth, cachedHeight);
    final int count = 1 << (depth - cachedHeight);
    checkArgument(
        cachedNodes.size() == count * Bytes32.SIZE,
        "Expected %s cached VRF nodes but got %s bytes",
        count,
        cachedNodes.size());
    final Bytes32[] nodes = new Bytes32[count];
    for (int i = 0; i < count; i++) {
      nodes[i] = Bytes32.wrap(cachedNodes.slice(i * Bytes32.SIZE, Bytes32.SIZE));
    }
    return new VrfTree(seed, depth, cachedHeight, nodes);
  }

  public int getDepth() {
    return depth;
  }

  public int getCachedHeight() {
    return cachedHeight;
  }

  public Bytes32 getRoot() {
    return upperLayers.getLast()[0];
  }

  /** The nodes at the cached height, concatenated, for {@link #fromCachedNodes}. */
  public Bytes getCachedNodes() {
    return Bytes.concatenate(upperLayers.getFirst());
  }

  /**
   * compute_vrf_proof: the leaf at index {@code slot}, followed by its Merkle branch from the leaf
   * up.
   */
  public List<Bytes32> computeProof(final UInt64 slot) {
    checkArgument(
        slot.isLessThan(UInt64.valueOf(1L << depth)),
        "Slot %s is beyond the VRF tree's %s leaves",
        slot,
        1L << depth);
    final long leafIndex = slot.longValue();
    final List<Bytes32> proof = new ArrayList<>(depth + 1);

    // Below the cached height: build the subtree holding the leaf and read the siblings from it
    final long subtreeStart = (leafIndex >>> cachedHeight) << cachedHeight;
    Bytes32[] layer = new Bytes32[1 << cachedHeight];
    for (int i = 0; i < layer.length; i++) {
      layer[i] = computeLeaf(seed, UInt64.valueOf(subtreeStart + i));
    }
    proof.add(layer[(int) (leafIndex - subtreeStart)]);
    for (int height = 0; height < cachedHeight; height++) {
      final int indexInLayer = (int) ((leafIndex - subtreeStart) >>> height);
      proof.add(layer[indexInLayer ^ 1]);
      layer = parentLayer(layer);
    }

    // From the cached height up: read the siblings from the cached layers
    for (int height = cachedHeight; height < depth; height++) {
      final Bytes32[] nodes = upperLayers.get(height - cachedHeight);
      proof.add(nodes[(int) ((leafIndex >>> height) ^ 1)]);
    }
    return proof;
  }

  /** compute_vrf_leaf: {@code keccak256(vrf_seed ‖ uint_to_bytes(index))}. */
  public static Bytes32 computeLeaf(final Bytes32 seed, final UInt64 index) {
    return Hash.keccak256(Bytes.concatenate(seed, uint64ToBytes(index)));
  }

  /**
   * compute_vrf_node: the node at {@code index} among the nodes {@code height} levels above the
   * leaves. Hashes the 2^{@code height} leaves under it, keeping only one pending node per level.
   */
  public static Bytes32 computeNode(final Bytes32 seed, final int height, final long index) {
    checkArgument(height >= 0 && height < Long.SIZE - 1, "Invalid VRF tree height %s", height);
    final long firstLeaf = index << height;
    final Bytes32[] pending = new Bytes32[height + 1];
    for (long i = 0; i < (1L << height); i++) {
      Bytes32 node = computeLeaf(seed, UInt64.valueOf(firstLeaf + i));
      // Each trailing one bit of i closes a complete subtree with the pending left sibling
      int level = 0;
      for (long bits = i; (bits & 1) == 1; bits >>>= 1) {
        node = hashPair(pending[level], node);
        level++;
      }
      pending[level] = node;
    }
    return pending[height];
  }

  /**
   * The root that {@code proof} opens for {@code slot}: the walk in is_valid_vrf_proof. Only the
   * low {@code proof.size() - 1} bits of the slot select the leaf.
   */
  public static Bytes32 computeRootFromProof(final List<Bytes32> proof, final UInt64 slot) {
    checkArgument(!proof.isEmpty(), "A VRF proof holds at least the leaf");
    Bytes32 value = proof.getFirst();
    for (int height = 0; height < proof.size() - 1; height++) {
      final Bytes32 sibling = proof.get(height + 1);
      // UInt64 holds the slot's unsigned bits, so an unsigned shift reads them
      value =
          ((slot.longValue() >>> height) & 1) == 1
              ? hashPair(sibling, value)
              : hashPair(value, sibling);
    }
    return value;
  }

  /**
   * Receives progress while a tree is derived. Called from the deriving threads, so implementations
   * must be thread-safe.
   */
  @FunctionalInterface
  public interface DeriveProgressListener {
    DeriveProgressListener NOOP = (derivedNodes, totalNodes) -> {};

    /** {@code derivedNodes} of the {@code totalNodes} cached nodes have been derived. */
    void onProgress(int derivedNodes, int totalNodes);
  }

  private static void checkShape(final int depth, final int cachedHeight) {
    checkArgument(depth > 0 && depth < Integer.SIZE, "Invalid VRF tree depth %s", depth);
    checkArgument(
        cachedHeight >= 0 && cachedHeight <= depth && depth - cachedHeight < Integer.SIZE - 2,
        "Invalid cached height %s for depth %s",
        cachedHeight,
        depth);
  }

  private List<Bytes32[]> buildUpperLayers(final Bytes32[] cachedNodes) {
    final List<Bytes32[]> layers = new ArrayList<>(depth - cachedHeight + 1);
    Bytes32[] layer = cachedNodes;
    layers.add(layer);
    for (int height = cachedHeight; height < depth; height++) {
      layer = parentLayer(layer);
      layers.add(layer);
    }
    return layers;
  }

  private static Bytes32[] parentLayer(final Bytes32[] layer) {
    final Bytes32[] parents = new Bytes32[layer.length / 2];
    for (int i = 0; i < parents.length; i++) {
      parents[i] = hashPair(layer[2 * i], layer[2 * i + 1]);
    }
    return parents;
  }

  private static Bytes32 hashPair(final Bytes32 left, final Bytes32 right) {
    return Hash.keccak256(Bytes.concatenate(left, right));
  }
}
