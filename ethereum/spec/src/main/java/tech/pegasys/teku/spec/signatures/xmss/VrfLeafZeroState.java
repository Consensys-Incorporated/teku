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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.PqConstants;

/**
 * A VRF tree known by its root and the siblings of leaf 0, the "compact state at slot 0" that PQ
 * devnet tooling (ethereum-package {@code vrf_state_url}) precomputes for each validator. It saves
 * deriving the whole tree, about 2^32 hashes.
 *
 * <p>The branch of the leaf for slot {@code s} needs, at each height {@code h}, the node at index
 * {@code (s >> h) ^ 1}. Index 1 is a stored sibling of leaf 0 and index 0 is on leaf 0's path, so
 * only the nodes right of leaf 0 at the heights below the top bit of {@code s} are hashed: about
 * {@code s} leaves at most. That is cheap while slots are small, as on a young devnet, and grows
 * with the slot.
 *
 * <p>The serialized state is {@code root ‖ uint32_le(0) ‖ sibling_0 ‖ … ‖ sibling_30}, 1028 bytes,
 * where {@code sibling_h} is the node at height {@code h} and index 1.
 */
public final class VrfLeafZeroState implements VrfProver {

  public static final int SERIALIZED_SIZE =
      Bytes32.SIZE + Integer.BYTES + PqConstants.VRF_TREE_DEPTH * Bytes32.SIZE;

  private final Bytes32 seed;
  private final int depth;
  private final Bytes32 root;
  // The siblings of leaf 0, by height
  private final Bytes32[] siblings;
  // The nodes on leaf 0's path (index 0), by height, from leaf 0 itself up
  private final Bytes32[] leftPath;

  private VrfLeafZeroState(final Bytes32 seed, final Bytes32 root, final Bytes32[] siblings) {
    this.seed = seed;
    this.depth = siblings.length;
    this.root = root;
    this.siblings = siblings;
    this.leftPath = new Bytes32[depth + 1];
    leftPath[0] = VrfTree.computeLeaf(seed, UInt64.ZERO);
    for (int height = 0; height < depth; height++) {
      leftPath[height + 1] = VrfTree.hashPair(leftPath[height], siblings[height]);
    }
  }

  /**
   * Restores the state of the tree derived from {@code seed} from its serialized form.
   *
   * @throws IllegalArgumentException if the state is malformed, isn't for slot 0, or doesn't belong
   *     to {@code seed}: leaf 0 of the seed's tree and the siblings must open to the root
   */
  public static VrfLeafZeroState fromBytes(final Bytes32 seed, final Bytes state) {
    return fromBytes(seed, PqConstants.VRF_TREE_DEPTH, state);
  }

  /** Restores a state of a tree of {@code depth}; real trees use the spec's depth. */
  public static VrfLeafZeroState fromBytes(final Bytes32 seed, final int depth, final Bytes state) {
    final int expectedSize = Bytes32.SIZE + Integer.BYTES + depth * Bytes32.SIZE;
    checkArgument(
        state.size() == expectedSize,
        "Expected a %s byte VRF state but got %s bytes",
        expectedSize,
        state.size());
    final int slot = state.getInt(Bytes32.SIZE, ByteOrder.LITTLE_ENDIAN);
    checkArgument(slot == 0, "Only VRF states at slot 0 are supported, not slot %s", slot);
    final Bytes32 root = Bytes32.wrap(state.slice(0, Bytes32.SIZE));
    final Bytes32[] siblings = new Bytes32[depth];
    for (int height = 0; height < depth; height++) {
      siblings[height] =
          Bytes32.wrap(
              state.slice(Bytes32.SIZE + Integer.BYTES + height * Bytes32.SIZE, Bytes32.SIZE));
    }
    final VrfLeafZeroState vrfState = new VrfLeafZeroState(seed, root, siblings);
    checkArgument(
        vrfState.leftPath[depth].equals(root),
        "VRF state with root %s was not derived from this VRF seed",
        root);
    return vrfState;
  }

  /**
   * Finds the state of {@code seed}'s tree among {@code states}, serialized states one after
   * another, as in a devnet {@code vrf_state_url} file. Each is checked by opening leaf 0 of the
   * seed's tree with its siblings, 31 hashes.
   */
  public static Optional<VrfLeafZeroState> find(final Bytes32 seed, final Bytes states) {
    return find(seed, PqConstants.VRF_TREE_DEPTH, states);
  }

  /** Finds a state of a tree of {@code depth}; real trees use the spec's depth. */
  public static Optional<VrfLeafZeroState> find(
      final Bytes32 seed, final int depth, final Bytes states) {
    final int serializedSize = Bytes32.SIZE + Integer.BYTES + depth * Bytes32.SIZE;
    final Bytes32 leafZero = VrfTree.computeLeaf(seed, UInt64.ZERO);
    for (int offset = 0; offset + serializedSize <= states.size(); offset += serializedSize) {
      final int siblingsOffset = offset + Bytes32.SIZE + Integer.BYTES;
      Bytes32 node = leafZero;
      for (int height = 0; height < depth; height++) {
        node =
            VrfTree.hashPair(
                node,
                Bytes32.wrap(states.slice(siblingsOffset + height * Bytes32.SIZE, Bytes32.SIZE)));
      }
      if (node.equals(Bytes32.wrap(states.slice(offset, Bytes32.SIZE)))) {
        return Optional.of(fromBytes(seed, depth, states.slice(offset, serializedSize)));
      }
    }
    return Optional.empty();
  }

  /** The serialized state, as {@link #fromBytes} reads it. */
  public Bytes toBytes() {
    return Bytes.concatenate(
        root, Bytes.wrap(new byte[Integer.BYTES]), Bytes.concatenate(siblings));
  }

  @Override
  public Bytes32 getRoot() {
    return root;
  }

  @Override
  public List<Bytes32> computeProof(final UInt64 slot) {
    checkArgument(
        slot.isLessThan(UInt64.valueOf(1L << depth)),
        "Slot %s is beyond the VRF tree's %s leaves",
        slot,
        1L << depth);
    final long leafIndex = slot.longValue();
    final List<Bytes32> proof = new ArrayList<>(depth + 1);
    proof.add(leafIndex == 0 ? leftPath[0] : VrfTree.computeLeaf(seed, UInt64.valueOf(leafIndex)));
    for (int height = 0; height < depth; height++) {
      final long siblingIndex = (leafIndex >>> height) ^ 1;
      if (siblingIndex == 1) {
        proof.add(siblings[height]);
      } else if (siblingIndex == 0) {
        proof.add(leftPath[height]);
      } else {
        proof.add(VrfTree.computeNode(seed, height, siblingIndex));
      }
    }
    return proof;
  }
}
