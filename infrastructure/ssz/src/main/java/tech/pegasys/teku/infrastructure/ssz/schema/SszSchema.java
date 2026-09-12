/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

/**
 * Base class for any SSZ structure schema like Vector, List, Container, primitive types
 * (https://github.com/ethereum/consensus-specs/blob/master/ssz/simple-serialize.md#typing)
 */
public interface SszSchema<SszDataT extends SszData> extends SszType {

  @SuppressWarnings("unchecked")
  static <X extends SszData> SszSchema<X> as(final Class<X> clazz, final SszSchema<?> schema) {
    return (SszSchema<X>) schema;
  }

  default Optional<String> getName() {
    return Optional.empty();
  }

  /**
   * Creates a default backing binary tree for this schema
   *
   * <p>E.g. if the schema is primitive then normally just a single leaf node is created
   *
   * <p>E.g. if the schema is a complex structure with multi-level nested vectors and containers
   * then the complete tree including all descendant members subtrees is created
   */
  TreeNode getDefaultTree();

  /**
   * Creates immutable ssz structure over the tree which should correspond to this schema.
   *
   * <p>Note: if the tree structure doesn't correspond this schema that fact could only be detected
   * later during access to structure members
   */
  SszDataT createFromBackingNode(TreeNode node);

  /** Returns the default immutable structure of this scheme */
  default SszDataT getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  boolean isPrimitive();

  default Bytes sszSerialize(final SszDataT view) {
    return sszSerializeTree(view.getBackingNode());
  }

  default int sszSerialize(final SszDataT view, final SszWriter writer) {
    return sszSerializeTree(view.getBackingNode(), writer);
  }

  default SszDataT sszDeserialize(final SszReader reader) throws SszDeserializeException {
    return createFromBackingNode(sszDeserializeTree(reader));
  }

  default SszDataT sszDeserialize(final Bytes ssz) throws SszDeserializeException {
    return sszDeserialize(SszReader.fromBytes(ssz));
  }

  /**
   * Validation applied by the gossip and RPC decoders after {@link #sszDeserialize(Bytes)}. Empty
   * means there is nothing to check beyond SSZ itself.
   *
   * <p>Only schemas decoded directly from the network declare a validator; nested schemas must not,
   * since nothing consults them.
   */
  default Optional<SszNetworkValidator<SszDataT>> getNetworkSszValidator() {
    return Optional.empty();
  }

  DeserializableTypeDefinition<SszDataT> getJsonTypeDefinition();

  default void jsonSerialize(final SszDataT view, final JsonGenerator gen) throws IOException {
    getJsonTypeDefinition().serialize(view, gen);
  }

  default SszDataT jsonDeserialize(final JsonParser parser) throws IOException {
    return getJsonTypeDefinition().deserialize(parser);
  }

  /**
   * Store the backing nodes for this object and its children. Iteration will be optimised by
   * skipping any branches reported as unnecessary by {@link TreeNodeStore#canSkipBranch(Bytes32,
   * long)} and by skipping up to {@code maxBranchLevelsSkipped} levels of branch nodes. Skipped
   * levels will never include the deepest level of nodes represented by this schema (ie the root
   * node of all child objects will be stored, even if it's a branch node).
   *
   * @param nodeStore the class to use to store node data
   * @param maxBranchLevelsSkipped the maximum number of levels of branch nodes that can be skipped
   * @param rootGIndex the generalized index of the root node of this schema
   */
  void storeBackingNodes(
      TreeNodeStore nodeStore, int maxBranchLevelsSkipped, long rootGIndex, TreeNode node);

  /**
   * Load backing nodes for this object and its children. This should be able to restore a tree
   * previously recorded with {@link #storeBackingNodes(TreeNodeStore, int, long, TreeNode)}.
   *
   * @param nodeSource the node source to load data from
   * @param rootHash the hash of the root node for the object to load
   * @param rootGIndex the GIndex of the root node for the object to load in the overall tree
   * @return the loaded node
   */
  TreeNode loadBackingNodes(TreeNodeSource nodeSource, Bytes32 rootHash, long rootGIndex);

  default SszDataT load(
      final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
    final TreeNode node = loadBackingNodes(nodeSource, rootHash, rootGIndex);
    return createFromBackingNode(node);
  }
}
