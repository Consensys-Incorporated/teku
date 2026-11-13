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

package tech.pegasys.teku.spec.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LazyBranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * A validator read from a registry packed in supernodes is a view over the supernode's bytes: it
 * must be indistinguishable from the view backed by its own tree node. The registry is a bounded
 * list up to Fulu and a progressive list from Gloas.
 */
@TestSpecContext(milestone = {SpecMilestone.ELECTRA, SpecMilestone.GLOAS})
class ValidatorPackedViewTest {
  // 2 * 256 + 37 validators: two full depth-8 supernodes and a partial one
  private static final int COUNT = 549;

  private DataStructureUtil dataStructureUtil;
  private SszListSchema<Validator, ?> registrySchema;
  private List<Validator> validators;
  // Created from elements: a progressive registry is then a tree of element nodes, whose views are
  // tree-backed, while a bounded one is packed in supernodes all the same
  private SszList<Validator> treeRegistry;
  // SSZ deserialization is the path that packs supernodes
  private SszList<Validator> packedRegistry;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    registrySchema =
        specContext
            .getSpec()
            .getGenesisSchemaDefinitions()
            .getBeaconStateSchema()
            .getValidatorsSchema();
    validators = new ArrayList<>(COUNT);
    for (int i = 0; i < COUNT; i++) {
      final Validator validator = dataStructureUtil.randomValidator();
      // cover both values of the bit and uint64 values of 2^63 and more
      validators.add(
          validator
              .withSlashed(i % 3 == 0)
              .withExitEpoch(i % 5 == 0 ? UInt64.MAX_VALUE : validator.getExitEpoch())
              .withWithdrawableEpoch(UInt64.fromLongBits(Long.MIN_VALUE + i)));
    }
    treeRegistry = registrySchema.createFromElements(validators);
    packedRegistry = registrySchema.sszDeserialize(treeRegistry.sszSerialize());
  }

  @TestTemplate
  void readsEveryFieldLikeTheTreeBackedView() {
    assertThat(packedRegistry.hashTreeRoot()).isEqualTo(treeRegistry.hashTreeRoot());
    for (int i = 0; i < COUNT; i++) {
      final Validator expected = validators.get(i);
      final Validator packed = packedRegistry.get(i);

      assertThat(packed.getPubkeyBytes()).isEqualTo(expected.getPubkeyBytes());
      assertThat(packed.getPublicKey()).isEqualTo(expected.getPublicKey());
      assertThat(packed.getWithdrawalCredentials()).isEqualTo(expected.getWithdrawalCredentials());
      assertThat(packed.getEffectiveBalance()).isEqualTo(expected.getEffectiveBalance());
      assertThat(packed.isSlashed()).isEqualTo(expected.isSlashed());
      assertThat(packed.getActivationEligibilityEpoch())
          .isEqualTo(expected.getActivationEligibilityEpoch());
      assertThat(packed.getActivationEpoch()).isEqualTo(expected.getActivationEpoch());
      assertThat(packed.getExitEpoch()).isEqualTo(expected.getExitEpoch());
      assertThat(packed.getWithdrawableEpoch()).isEqualTo(expected.getWithdrawableEpoch());
    }
  }

  @TestTemplate
  void hashesSerializesAndComparesLikeTheTreeBackedView() {
    for (int i = 0; i < COUNT; i++) {
      final Validator expected = validators.get(i);
      final Validator packed = packedRegistry.get(i);

      // equality, hash code, the generic child views, serialization and hash tree root
      assertThatSszData(packed).isEqualByAllMeansTo(expected);
      assertThat(expected).isEqualTo(packed);
      assertThat(packed.toString()).isEqualTo(expected.toString());
    }
  }

  @TestTemplate
  void copiesWithAChangedFieldLikeTheTreeBackedView() {
    final Validator expected = validators.get(300);
    final Validator packed = packedRegistry.get(300);
    final UInt64 value = UInt64.valueOf(123456789);

    assertThat(packed.withEffectiveBalance(value)).isEqualTo(expected.withEffectiveBalance(value));
    assertThat(packed.withSlashed(true)).isEqualTo(expected.withSlashed(true));
    assertThat(packed.withActivationEligibilityEpoch(value))
        .isEqualTo(expected.withActivationEligibilityEpoch(value));
    assertThat(packed.withActivationEpoch(value)).isEqualTo(expected.withActivationEpoch(value));
    assertThat(packed.withExitEpoch(value)).isEqualTo(expected.withExitEpoch(value));
    assertThat(packed.withWithdrawableEpoch(value))
        .isEqualTo(expected.withWithdrawableEpoch(value));
    assertThat(packed.withWithdrawalCredentials(dataStructureUtil.randomBytes32()).getPubkeyBytes())
        .isEqualTo(expected.getPubkeyBytes());
  }

  @TestTemplate
  void readsRegistriesOfSizesAroundTheSupernodeAndProgressiveLevelBoundaries() {
    for (final int size : List.of(0, 1, 2, 5, 21, 85, 255, 256, 257, 341, 512, 513)) {
      final List<Validator> expected = validators.subList(0, size);
      final SszList<Validator> packed =
          registrySchema.sszDeserialize(registrySchema.createFromElements(expected).sszSerialize());

      assertThat(packed.size()).isEqualTo(size);
      for (int i = 0; i < size; i++) {
        assertThatSszData(packed.get(i))
            .describedAs("validator %s of %s", i, size)
            .isEqualByAllMeansTo(expected.get(i));
      }
      assertThat(packed.asList()).isEqualTo(expected);
    }
  }

  @TestTemplate
  void doesNotKeepTheViewsOfThePackedRegistry() {
    // a view is created on every access instead of being cached for the life of the list
    assertThat(packedRegistry.get(7)).isNotSameAs(packedRegistry.get(7));
    assertThat(packedRegistry.get(7)).isEqualTo(packedRegistry.get(7));
  }

  @TestTemplate
  void readsEveryElementOfAPackedRegistryFromItsPackedBytes(final SpecContext specContext) {
    // Level 0 of a progressive list, its first element, is a plain node and never a supernode
    final int firstPacked =
        specContext.getSpecMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS) ? 1 : 0;
    for (int i = 0; i < COUNT; i++) {
      assertThat(packedRegistry.get(i).isPackedView())
          .describedAs("validator %s", i)
          .isEqualTo(i >= firstPacked);
    }
  }

  @TestTemplate
  void readsAProgressiveRegistryBuiltFromElementsThroughItsTreeNodes(
      final SpecContext specContext) {
    specContext.assumeMilestoneActive(SpecMilestone.GLOAS);
    for (int i = 0; i < COUNT; i++) {
      assertThat(treeRegistry.get(i).isPackedView()).describedAs("validator %s", i).isFalse();
    }
  }

  @TestTemplate
  void readingOneValidatorLoadsOnlyItsSupernode() {
    final AtomicInteger loadedSuperNodes = new AtomicInteger();
    final SszList<Validator> lazyRegistry =
        registrySchema.createFromBackingNode(
            loadedLazily(packedRegistry.getBackingNode(), loadedSuperNodes));

    assertThat(lazyRegistry.get(300)).isEqualTo(validators.get(300));
    assertThat(loadedSuperNodes).hasValue(1);

    // the same supernode again, then one of another supernode
    assertThat(lazyRegistry.get(301)).isEqualTo(validators.get(301));
    assertThat(loadedSuperNodes).hasValue(1);
    assertThat(lazyRegistry.get(540)).isEqualTo(validators.get(540));
    assertThat(loadedSuperNodes).hasValue(2);
  }

  /** A copy of the tree whose branches load their children on demand, counting the supernodes. */
  private static TreeNode loadedLazily(final TreeNode node, final AtomicInteger loadedSuperNodes) {
    if (node instanceof BranchNode branch) {
      return LazyBranchNode.createWithUnknownHash(
          branch.left().hashTreeRoot(),
          branch.right().hashTreeRoot(),
          () -> loadedLazily(branch.left(), loadedSuperNodes),
          () -> loadedLazily(branch.right(), loadedSuperNodes));
    }
    if (node instanceof SszSuperNode) {
      loadedSuperNodes.incrementAndGet();
    }
    return node;
  }

  @TestTemplate
  void readsTheRegistryBuiltFromElementsThroughItsTreeNodes() {
    for (int i = 0; i < COUNT; i++) {
      assertThatSszData(treeRegistry.get(i)).isEqualByAllMeansTo(validators.get(i));
    }
  }

  @TestTemplate
  void storesAPackedViewInAnotherRegistryAndReadsChangesBack() {
    final SszMutableList<Validator> mutable = packedRegistry.createWritableCopy();
    final UInt64 newBalance = UInt64.valueOf(31_000_000_000L);
    // read through the mutable list, change, write back: the pattern of the state mutators
    mutable.set(3, mutable.get(3).withEffectiveBalance(newBalance));
    // a packed view of one registry stored as is at another index
    mutable.set(260, packedRegistry.get(5));
    mutable.append(packedRegistry.get(6));
    assertThat(mutable.get(3).getEffectiveBalance()).isEqualTo(newBalance);
    assertThat(mutable.get(260)).isEqualTo(validators.get(5));
    assertThat(mutable.get(4)).isEqualTo(validators.get(4));

    final List<Validator> expected = new ArrayList<>(validators);
    expected.set(3, validators.get(3).withEffectiveBalance(newBalance));
    expected.set(260, validators.get(5));
    expected.add(validators.get(6));
    final SszList<Validator> committed = mutable.commitChanges();

    assertThat(committed.hashTreeRoot())
        .isEqualTo(registrySchema.createFromElements(expected).hashTreeRoot());
    for (int i = 0; i < expected.size(); i++) {
      assertThatSszData(committed.get(i)).isEqualByAllMeansTo(expected.get(i));
    }
    // the original registry is untouched
    assertThat(packedRegistry.get(3)).isEqualTo(validators.get(3));
  }
}
