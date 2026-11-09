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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.traversal.OrderedTreeStream;

/** A tree where each node is identified by a Bytes32 hash */
public class HashTree {

  private final Bytes32 rootHash;
  private final Map<Bytes32, Set<Bytes32>> parentToChildren;
  private final Map<Bytes32, Bytes32> childToParent;

  private HashTree(
      final Bytes32 rootHash,
      final Map<Bytes32, Bytes32> childToParent,
      final Map<Bytes32, Set<Bytes32>> parentToChildren) {
    this.rootHash = rootHash;
    this.parentToChildren = parentToChildren;
    this.childToParent = childToParent;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder updater() {
    return withRoot(rootHash);
  }

  /**
   * Create a new tree rooted at the given hash, pruning any nodes that do not descend from the new
   * root.
   *
   * @param newRootHash The new root of the tree
   * @return A new {@code HashTree} containing only nodes that descend from the new root.
   */
  public Builder withRoot(final Bytes32 newRootHash) {
    Builder builder = builder().rootHash(newRootHash);
    preOrderStream(newRootHash)
        .forEach(
            root -> getParent(root).ifPresent(parent -> builder.childAndParentRoots(root, parent)));

    return builder;
  }

  public Bytes32 getRootHash() {
    return rootHash;
  }

  public Optional<Bytes32> getParent(final Bytes32 childRoot) {
    return Optional.ofNullable(childToParent.get(childRoot));
  }

  public Set<Bytes32> getAllRoots() {
    return new HashSet<>(childToParent.keySet());
  }

  /**
   * Process each node in the chain defined by {@code head}
   *
   * @param head The root defining the head of the chain to process
   * @param processor The callback to invoke for each child-parent pair
   */
  public void processHashesInChain(final Bytes32 head, final NodeProcessor processor) {
    processHashesInChainWhile(head, HaltableNodeProcessor.fromNodeProcessor(processor));
  }

  /**
   * Accumulate the list of roots from the provided head hash up to the root hash. Stops
   * accumulating roots when {@code shouldContinue} returns false.
   *
   * @param head The root defining the head of the chain to construct
   * @param shouldContinue The condition determining when to stop collecting ancestor roots
   * @return A list of roots in ascending order belonging to the chain defined by {@code head}
   */
  public List<Bytes32> collectChainRoots(
      final Bytes32 head, final Function<Bytes32, Boolean> shouldContinue) {
    final Deque<Bytes32> chain = new ArrayDeque<>();
    processHashesInChainWhile(
        head,
        (child, parent) -> {
          chain.addFirst(child);
          return shouldContinue.apply(child);
        });
    return new ArrayList<>(chain);
  }

  /**
   * Process roots in the chain defined by {@code head}. Stops processing when {@code
   * shouldContinue} returns false.
   *
   * @param head The root defining the head of the chain to construct
   * @param nodeProcessor The callback receiving hashes and determining whether to continue
   *     processing
   */
  public void processHashesInChainWhile(
      final Bytes32 head, final HaltableNodeProcessor nodeProcessor) {
    checkArgument(contains(head), "Unknown root supplied: %s", head);

    Optional<Bytes32> currentRoot = Optional.of(head);
    Optional<Bytes32> parentRoot = currentRoot.flatMap(this::getParent);
    while (parentRoot.isPresent()) {
      final boolean shouldContinue = nodeProcessor.process(currentRoot.get(), parentRoot.get());
      if (!shouldContinue) {
        break;
      }
      currentRoot = parentRoot;
      parentRoot = currentRoot.flatMap(this::getParent);
    }
  }

  public int countChildren(final Bytes32 hash) {
    return Optional.ofNullable(parentToChildren.get(hash)).map(Set::size).orElse(0);
  }

  public boolean contains(final Bytes32 blockRoot) {
    return childToParent.containsKey(blockRoot);
  }

  public int size() {
    return childToParent.size();
  }

  /**
   * @return A stream of all tree nodes in pre-order traversal order
   */
  public Stream<Bytes32> preOrderStream() {
    return preOrderStream(rootHash);
  }

  /**
   * @return A stream of all tree nodes in breadth-first traversal order
   */
  public Stream<Bytes32> breadthFirstStream() {
    return createBreadthFirstStream(rootHash);
  }

  private Stream<Bytes32> preOrderStream(final Bytes32 rootNodeHash) {
    if (!contains(rootNodeHash)) {
      return Stream.empty();
    }
    return OrderedTreeStream.createPreOrderTraversalStream(rootNodeHash, parentToChildren::get);
  }

  private Stream<Bytes32> createBreadthFirstStream(final Bytes32 rootNodeHash) {
    if (!contains(rootNodeHash)) {
      return Stream.empty();
    }
    return OrderedTreeStream.createBreadthFirstStream(rootNodeHash, parentToChildren::get);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof HashTree)) {
      return false;
    }
    HashTree hashTree = (HashTree) o;
    return Objects.equals(rootHash, hashTree.rootHash)
        && Objects.equals(childToParent, hashTree.childToParent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootHash, childToParent);
  }

  public static class Builder {
    private Bytes32 rootHash = null;
    private final Map<Bytes32, Bytes32> childToParentMap = new HashMap<>();

    public HashTree build() {
      assertValid();

      // Save child / parent mappings
      final Map<Bytes32, Set<Bytes32>> parentToChildLookup = new HashMap<>();
      for (Map.Entry<Bytes32, Bytes32> childToParent : childToParentMap.entrySet()) {
        final Set<Bytes32> childSet =
            // Use TreeSet for predictable ordering of children that tests can rely on
            parentToChildLookup.computeIfAbsent(childToParent.getValue(), (key) -> new TreeSet<>());
        childSet.add(childToParent.getKey());
      }

      // Prune child-parent map to contain only hashes that descend from the root
      final Map<Bytes32, Bytes32> prunedChildToParentMap = new HashMap<>();
      OrderedTreeStream.createPreOrderTraversalStream(rootHash, parentToChildLookup::get)
          .forEach(root -> prunedChildToParentMap.put(root, childToParentMap.get(root)));
      // Prune parent-child lookup
      Sets.difference(childToParentMap.keySet(), prunedChildToParentMap.keySet())
          .forEach(parentToChildLookup::remove);

      return new HashTree(rootHash, prunedChildToParentMap, parentToChildLookup);
    }

    private void assertValid() {
      checkNotNull(rootHash, "Must supply a root block");
      checkState(
          childToParentMap.containsKey(rootHash), "Must provide parent information for root");
    }

    public boolean contains(final Bytes32 hash) {
      return childToParentMap.containsKey(hash);
    }

    public Builder rootHash(final Bytes32 rootBlockHash) {
      checkNotNull(rootBlockHash);
      this.rootHash = rootBlockHash;
      return this;
    }

    public Builder childAndParentRoots(final Map<Bytes32, Bytes32> childToParentMap) {
      checkNotNull(childToParentMap);
      this.childToParentMap.putAll(childToParentMap);
      return this;
    }

    public Builder childAndParentRoots(final Bytes32 childRoot, final Bytes32 parentRoot) {
      checkNotNull(childRoot);
      checkNotNull(parentRoot);
      childToParentMap.put(childRoot, parentRoot);
      return this;
    }

    public Builder block(final BeaconBlockSummary block) {
      checkNotNull(block);
      return childAndParentRoots(block.getRoot(), block.getParentRoot());
    }

    public Builder blocks(final Collection<? extends BeaconBlockSummary> blocks) {
      checkNotNull(blocks);
      blocks.forEach(this::block);
      return this;
    }
  }

  public interface NodeProcessor {
    /**
     * Process parent and child roots
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     */
    void process(final Bytes32 childRoot, final Bytes32 parentRoot);
  }

  public interface HaltableNodeProcessor {

    static HaltableNodeProcessor fromNodeProcessor(final NodeProcessor processor) {
      return (child, parent) -> {
        processor.process(child, parent);
        return true;
      };
    }

    /**
     * Process parent and child and return a status indicating whether to continue
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     * @return True if processing should continue, false if processing should halt
     */
    boolean process(final Bytes32 childRoot, final Bytes32 parentRoot);
  }
}
