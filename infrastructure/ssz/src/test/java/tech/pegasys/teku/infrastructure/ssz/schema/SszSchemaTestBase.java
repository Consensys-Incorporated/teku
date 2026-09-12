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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.infrastructure.ssz.schema.TreeNodeAssert.assertThatTreeNode;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints.SszSuperNodeHint;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SimpleSszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class SszSchemaTestBase extends SszTypeTestBase {

  protected final RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getDefaultTree_shouldBeEqualToDefaultStructure(final SszSchema<SszData> schema) {
    SszData defaultTreeData = schema.createFromBackingNode(schema.getDefaultTree());
    SszDataAssert.assertThatSszData(defaultTreeData).isEqualByAllMeansTo(schema.getDefault());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getNetworkSszValidator_shouldBeEmptyByDefault(final SszSchema<SszData> schema) {
    assertThat(schema.getNetworkSszValidator()).isEmpty();
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void sszDeserialize_tooLongSszShouldFailFastWithoutReadingWholeInput(
      final SszSchema<SszData> schema) {
    long maxSszLength = schema.getSszLengthBounds().getMaxBytes();
    // ignore too large and degenerative structs
    assumeThat(maxSszLength).isLessThan(32 * 1024 * 1024).isGreaterThan(0);
    // ignore lists using SszSuperNode as many validations are skipped
    if (schema instanceof AbstractSszCollectionSchema<?, ?> collectionSchema) {
      assumeThat(collectionSchema.getHints().getHint(SszSuperNodeHint.class))
          .describedAs("uses SszSuperNode")
          .isEmpty();
    }

    SszData data = randomSsz.randomData(schema);

    if (data instanceof SszOptional<?> optionalData) {
      assumeThat(optionalData.getValue())
          .describedAs("optional can't be empty to pass the test")
          .isPresent();
    }

    Bytes ssz = data.sszSerialize();

    Bytes sszWithExtraData = Bytes.wrap(ssz, Bytes.random((int) (maxSszLength - ssz.size() + 1)));
    AtomicInteger bytesCounter = new AtomicInteger();
    SimpleSszReader countingReader =
        new SimpleSszReader(sszWithExtraData) {
          @Override
          public Bytes read(final int length) {
            bytesCounter.addAndGet(length);
            return super.read(length);
          }
        };
    assertThatThrownBy(() -> schema.sszDeserialize(countingReader))
        .isInstanceOf(SszDeserializeException.class);
    assertThat(bytesCounter.get()).isLessThanOrEqualTo(ssz.size());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreTree_singleBranchStep(final SszSchema<?> schema) {
    // Up to 32768 child nodes can be included in a single step which is enough for all sane values
    // Bigger than this requires too much memory to load and we'd use multiple steps in reality
    final int maxBranchLevelsSkipped = 15;
    assertTreeRoundtrip(schema, maxBranchLevelsSkipped);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreTree_multipleBranchSteps(final SszSchema<?> schema) {
    final int maxBranchLevelsSkipped = 1;
    assertTreeRoundtrip(schema, maxBranchLevelsSkipped);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreTree_normalBranchSkipLevel(final SszSchema<?> schema) {
    final int maxBranchLevelsSkipped = 5;
    assertTreeRoundtrip(schema, maxBranchLevelsSkipped);
  }

  private void assertTreeRoundtrip(final SszSchema<?> schema, final int maxBranchLevelsSkipped) {
    // Find some non-zero data (to make sure it's actually different to the default tree)
    final SszData data =
        randomSsz
            .withMaxListSize(50)
            .randomDataStream(schema)
            .filter(item -> !item.getBackingNode().hashTreeRoot().isZero())
            .findFirst()
            .orElseThrow();
    assertTreeRoundtrip(schema, maxBranchLevelsSkipped, data);
  }

  protected void assertTreeRoundtrip(
      final SszSchema<?> schema, final int maxBranchLevelsSkipped, final SszData data) {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final TreeNode node = data.getBackingNode();
    final long rootGIndex = 34;
    schema.storeBackingNodes(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
    final TreeNode result = schema.loadBackingNodes(nodeStore, data.hashTreeRoot(), rootGIndex);
    assertThatTreeNode(result).isTreeEqual(node);
    final SszData rebuiltData = schema.createFromBackingNode(result);
    assertThat(rebuiltData).isEqualTo(data);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreDefaultTree(final SszSchema<?> schema) {
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    final TreeNode node = schema.getDefault().getBackingNode();
    final long rootGIndex = 67;
    schema.storeBackingNodes(nodeStore, 100, rootGIndex, node);
    // LeafNode should be equal
    final TreeNode result = schema.loadBackingNodes(nodeStore, node.hashTreeRoot(), rootGIndex);
    assertThatTreeNode(result).isTreeEqual(node);

    // And should be equal when accessing the actual value
    final SszData rebuiltData = schema.createFromBackingNode(result);
    assertThat(rebuiltData).isEqualTo(schema.createFromBackingNode(node));
  }
}
