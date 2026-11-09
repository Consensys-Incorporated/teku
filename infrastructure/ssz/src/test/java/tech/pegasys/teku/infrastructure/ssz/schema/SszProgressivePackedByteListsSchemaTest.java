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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszPackedByteListsHint;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNodeTest;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedProgressiveByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszProgressivePackedByteListsSchemaTest {

  private static final SszProgressiveByteListSchema<SszByteList> ELEMENT_SCHEMA =
      new SszProgressiveByteListSchema<>();

  private static final SszProgressiveListSchema<SszByteList> HINTED =
      SszProgressiveListSchema.create(ELEMENT_SCHEMA, SszSchemaHints.sszPackedByteLists());
  private static final SszProgressiveListSchema<SszByteList> UNHINTED =
      SszProgressiveListSchema.create(ELEMENT_SCHEMA);

  private static Bytes serialized(final int... sizes) {
    return SszPackedByteListsNodeTest.serializeElements(
        SszPackedByteListsNodeTest.elementsOfSizes(sizes));
  }

  private static TreeNode dataNode(final SszList<SszByteList> list) {
    return list.getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  @Test
  public void hint_shouldBeAcceptedForProgressiveByteListElements() {
    final SszProgressiveListSchema<SszByteList> schema =
        SszProgressiveListSchema.create(ELEMENT_SCHEMA, SszSchemaHints.sszPackedByteLists());
    assertThat(schema.getHints().getHint(SszPackedByteListsHint.class)).isPresent();
  }

  @Test
  public void hint_shouldBeRejectedForNonByteListElements() {
    assertThatThrownBy(
            () ->
                SszProgressiveListSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, SszSchemaHints.sszPackedByteLists()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void hint_shouldBeRejectedForBoundedByteListElements() {
    // catches accidental reuse of the fixed-list instanceof SszByteListSchema check
    assertThatThrownBy(
            () ->
                SszProgressiveListSchema.create(
                    SszByteListSchema.create(256), SszSchemaHints.sszPackedByteLists()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void hint_shouldBeRejectedTogetherWithSuperNodeHint() {
    assertThatThrownBy(
            () ->
                SszProgressiveListSchema.create(
                    ELEMENT_SCHEMA,
                    SszSchemaHints.of(
                        new SszPackedByteListsHint(), new SszSchemaHints.SszSuperNodeHint(3))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void fixedListHint_shouldRejectProgressiveByteListElements() {
    // SszProgressiveByteListSchema implements SszByteListSchema; without tightening this would
    // pass fixed validation and fail only at first deserialize via treeDepth()
    assertThatThrownBy(
            () -> SszListSchema.create(ELEMENT_SCHEMA, 16, SszSchemaHints.sszPackedByteLists()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void deserialize_shouldProducePackedNodeAndMatchOracle() {
    final Bytes bytes = serialized(1, 0, 33, 100, 160, 5, 7);
    final SszList<SszByteList> hinted = HINTED.sszDeserialize(bytes);
    final SszList<SszByteList> unhinted = UNHINTED.sszDeserialize(bytes);
    assertThat(dataNode(hinted)).isInstanceOf(SszPackedProgressiveByteListsNode.class);
    assertThat(hinted.hashTreeRoot()).isEqualTo(unhinted.hashTreeRoot());
    assertThat(hinted.size()).isEqualTo(7);
    for (int i = 0; i < hinted.size(); i++) {
      assertThat(hinted.get(i).getBytes()).isEqualTo(unhinted.get(i).getBytes());
    }
  }

  @Test
  public void deserialize_shouldMatchOracleForEmptyList() {
    final SszList<SszByteList> hinted = HINTED.sszDeserialize(Bytes.EMPTY);
    assertThat(dataNode(hinted)).isNotInstanceOf(SszPackedProgressiveByteListsNode.class);
    assertThat(hinted.hashTreeRoot())
        .isEqualTo(UNHINTED.sszDeserialize(Bytes.EMPTY).hashTreeRoot());
  }

  @Test
  public void deserialize_shouldRejectMalformedInputLikeOracle() {
    final List<Bytes> malformed =
        List.of(
            Bytes.of(1), // truncated below one offset word
            Bytes.of(1, 2),
            Bytes.of(1, 2, 3),
            Bytes.of(0, 0, 0, 0), // zero first offset
            Bytes.concatenate(Bytes.of(5, 0, 0, 0), Bytes.of(1)), // misaligned
            Bytes.concatenate(
                Bytes.of(8, 0, 0, 0), Bytes.of(7, 0, 0, 0), Bytes.of(1, 2)), // non-monotonic
            Bytes.concatenate(Bytes.of(12, 0, 0, 0), Bytes.of(1))); // first offset beyond end
    for (final Bytes bytes : malformed) {
      assertThatThrownBy(() -> HINTED.sszDeserialize(bytes))
          .describedAs("hinted %s", bytes)
          .isInstanceOf(SszDeserializeException.class);
      assertThatThrownBy(() -> UNHINTED.sszDeserialize(bytes))
          .describedAs("unhinted %s", bytes)
          .isInstanceOf(SszDeserializeException.class);
    }
  }

  @Test
  public void serialize_shouldRoundTripByteIdentical() {
    final Bytes bytes = serialized(1, 0, 33, 100, 160, 5, 7);
    final SszList<SszByteList> hinted = HINTED.sszDeserialize(bytes);
    assertThat(hinted.sszSerialize()).isEqualTo(bytes);
    assertThat(HINTED.getSszSize(hinted.getBackingNode()))
        .isEqualTo(UNHINTED.getSszSize(UNHINTED.sszDeserialize(bytes).getBackingNode()));
  }

  @Test
  public void createFromElements_shouldProducePackedNodeAndMatchOracle() {
    final List<SszByteList> elements =
        List.of(
            ELEMENT_SCHEMA.fromBytes(Bytes.of(1)),
            ELEMENT_SCHEMA.fromBytes(Bytes.EMPTY),
            ELEMENT_SCHEMA.fromBytes(Bytes.wrap(new byte[100])));
    final SszList<SszByteList> hinted = HINTED.createFromElements(elements);
    final SszList<SszByteList> unhinted = UNHINTED.createFromElements(elements);
    assertThat(dataNode(hinted)).isInstanceOf(SszPackedProgressiveByteListsNode.class);
    assertThat(hinted.hashTreeRoot()).isEqualTo(unhinted.hashTreeRoot());
    assertThat(hinted.sszSerialize()).isEqualTo(unhinted.sszSerialize());
    assertThat(HINTED.createFromElements(List.of()).hashTreeRoot())
        .isEqualTo(UNHINTED.createFromElements(List.of()).hashTreeRoot());
  }

  @Test
  public void mutation_shouldDecayThroughProductionRouteAndMatchOracle() {
    final Bytes bytes = serialized(1, 0, 33, 100, 160, 5, 7);
    final SszMutableList<SszByteList> hintedMutable =
        HINTED.sszDeserialize(bytes).createWritableCopy();
    final SszMutableList<SszByteList> unhintedMutable =
        UNHINTED.sszDeserialize(bytes).createWritableCopy();
    hintedMutable.set(1, ELEMENT_SCHEMA.fromBytes(Bytes.of(9, 9, 9)));
    unhintedMutable.set(1, ELEMENT_SCHEMA.fromBytes(Bytes.of(9, 9, 9)));
    final SszList<SszByteList> hinted = hintedMutable.commitChanges();
    final SszList<SszByteList> unhinted = unhintedMutable.commitChanges();
    assertThat(hinted.hashTreeRoot()).isEqualTo(unhinted.hashTreeRoot());
    assertThat(hinted.sszSerialize()).isEqualTo(unhinted.sszSerialize());
    assertThat(dataNode(hinted)).isNotInstanceOf(SszPackedProgressiveByteListsNode.class);

    // The spec mandates full decay: no stateless virtual wrappers or packed nodes referencing
    // the original buffer should survive into the committed tree for untouched levels.
    final List<String> retainedPackedNodes = new ArrayList<>();
    hinted
        .getBackingNode()
        .iterateAll(
            n -> {
              final String name = n.getClass().getName();
              if (n instanceof SszPackedProgressiveByteListsNode || name.contains("Virtual")) {
                retainedPackedNodes.add(name);
              }
            });
    assertThat(retainedPackedNodes).isEmpty();
  }

  @Test
  public void storeLoad_shouldRoundTripViaMaterializedForm() {
    final Bytes bytes = serialized(1, 0, 33, 100, 160, 5, 7);
    final SszList<SszByteList> hinted = HINTED.sszDeserialize(bytes);
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final TreeNode node = hinted.getBackingNode();
    HINTED.storeBackingNodes(nodeStore, 100, GIndexUtil.SELF_G_INDEX, node);
    final TreeNode loaded =
        HINTED.loadBackingNodes(nodeStore, node.hashTreeRoot(), GIndexUtil.SELF_G_INDEX);
    assertThat(loaded.hashTreeRoot()).isEqualTo(node.hashTreeRoot());
    final SszList<SszByteList> reloaded = HINTED.createFromBackingNode(loaded);
    for (int i = 0; i < hinted.size(); i++) {
      assertThat(reloaded.get(i).getBytes()).isEqualTo(hinted.get(i).getBytes());
    }
    assertThat(dataNode(reloaded)).isNotInstanceOf(SszPackedProgressiveByteListsNode.class);
  }
}
