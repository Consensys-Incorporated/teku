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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.ImmutableSubContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableMutableSubContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchemaTest;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszContainerTest implements SszCompositeTestBase, SszMutableRefCompositeTestBase {

  // ===== Progressive container schemas =====

  private static final SszContainerSchema<SszContainer> PROGRESSIVE_ALL_FIXED_SCHEMA =
      SszContainerSchema.createProgressive(
          "AllFixed",
          new boolean[] {true, true, true},
          List.of(
              NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
              NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
              NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA)));

  private static final SszContainerSchema<SszContainer> PROGRESSIVE_MIXED_SCHEMA =
      SszContainerSchema.createProgressive(
          "Mixed",
          new boolean[] {true, true},
          List.of(
              NamedSchema.of("fixed", SszPrimitiveSchemas.UINT64_SCHEMA),
              NamedSchema.of("var", SszBitlistSchema.create(100))));

  private static final SszContainerSchema<SszContainer> PROGRESSIVE_GAPPED_SCHEMA =
      SszContainerSchema.createProgressive(
          "Gapped",
          new boolean[] {true, false, true},
          List.of(
              NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
              NamedSchema.of("third", SszPrimitiveSchemas.BYTE_SCHEMA)));

  @Override
  public Stream<SszContainer> sszData() {
    RandomSszDataGenerator smallListsGen = new RandomSszDataGenerator().withMaxListSize(1);
    RandomSszDataGenerator largeListsGen = new RandomSszDataGenerator().withMaxListSize(1024);
    Stream<SszContainer> regularContainers =
        SszContainerSchemaTest.testContainerSchemas()
            .flatMap(
                schema ->
                    Stream.of(
                        schema.getDefault(),
                        smallListsGen.randomData(schema),
                        largeListsGen.randomData(schema)));
    Stream<SszContainer> progressiveContainers =
        Stream.of(
            PROGRESSIVE_ALL_FIXED_SCHEMA.createFromBackingNode(
                PROGRESSIVE_ALL_FIXED_SCHEMA.getDefaultTree()),
            createProgressiveAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3)),
            createProgressiveAllFixed(UInt64.valueOf(100), (byte) 0, UInt64.valueOf(200)),
            PROGRESSIVE_MIXED_SCHEMA.createFromBackingNode(
                PROGRESSIVE_MIXED_SCHEMA.getDefaultTree()),
            createProgressiveMixed(UInt64.valueOf(5), 10, 0, 3, 9),
            PROGRESSIVE_GAPPED_SCHEMA.createFromBackingNode(
                PROGRESSIVE_GAPPED_SCHEMA.getDefaultTree()),
            createProgressiveGapped(UInt64.valueOf(42), (byte) 7));
    return Stream.concat(regularContainers, progressiveContainers);
  }

  @Test
  public void readWriteContainerTest1() {
    WritableContainer c1 = WritableContainer.createDefault();

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getVector1().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getVector1().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1.getVector1().get(2);
              });
    }

    WritableMutableContainer c1w = c1.createWritableCopy();
    c1w.setLong1(UInt64.valueOf(0x1));
    c1w.setLong2(UInt64.valueOf(0x2));

    c1w.getSub1().setLong1(UInt64.valueOf(0x111));
    c1w.getSub1().setLong2(UInt64.valueOf(0x222));

    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x333)));
    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x444)));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UInt64.valueOf(0x555));
              sc.setLong2(UInt64.valueOf(0x666));
            });
    WritableMutableSubContainer sc1w = c1w.getList2().append();
    sc1w.setLong1(UInt64.valueOf(0x777));
    sc1w.setLong2(UInt64.valueOf(0x888));

    c1w.getVector1()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getVector1().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getVector1().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1w.getLong1()).isEqualTo(UInt64.valueOf(0x1));
      assertThat(c1w.getLong2()).isEqualTo(UInt64.valueOf(0x2));
      assertThat(c1w.getSub1().getLong1()).isEqualTo(UInt64.valueOf(0x111));
      assertThat(c1w.getSub1().getLong2()).isEqualTo(UInt64.valueOf(0x222));
      assertThat(c1w.getList1().size()).isEqualTo(2);
      assertThat(c1w.getList1().get(0).get()).isEqualTo(UInt64.valueOf(0x333));
      assertThat(c1w.getList1().get(1).get()).isEqualTo(UInt64.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1w.getList1().get(2);
              });
      assertThat(c1w.getList2().size()).isEqualTo(2);
      assertThat(c1w.getList2().get(0).getLong1()).isEqualTo(UInt64.valueOf(0x555));
      assertThat(c1w.getList2().get(0).getLong2()).isEqualTo(UInt64.valueOf(0x666));
      assertThat(c1w.getList2().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x777));
      assertThat(c1w.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
      assertThat(c1w.getVector1().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1w.getVector1().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1w.getVector1().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x999));
      assertThat(c1w.getVector1().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    WritableContainer c1r = c1w.commitChanges();

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getVector1().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getVector1().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getVector1().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1r.getLong1()).isEqualTo(UInt64.valueOf(0x1));
      assertThat(c1r.getLong2()).isEqualTo(UInt64.valueOf(0x2));
      assertThat(c1r.getSub1().getLong1()).isEqualTo(UInt64.valueOf(0x111));
      assertThat(c1r.getSub1().getLong2()).isEqualTo(UInt64.valueOf(0x222));
      assertThat(c1r.getList1().size()).isEqualTo(2);
      assertThat(c1r.getList1().get(0).get()).isEqualTo(UInt64.valueOf(0x333));
      assertThat(c1r.getList1().get(1).get()).isEqualTo(UInt64.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1r.getList1().get(2);
              });
      assertThat(c1r.getList2().size()).isEqualTo(2);
      assertThat(c1r.getList2().get(0).getLong1()).isEqualTo(UInt64.valueOf(0x555));
      assertThat(c1r.getList2().get(0).getLong2()).isEqualTo(UInt64.valueOf(0x666));
      assertThat(c1r.getList2().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x777));
      assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
      assertThat(c1r.getVector1().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1r.getVector1().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1r.getVector1().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x999));
      assertThat(c1r.getVector1().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    WritableMutableContainer c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(1).setLong2(UInt64.valueOf(0xaaa));
    WritableContainer c2r = c2w.commitChanges();

    assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
    assertThat(c2r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0xaaa));
  }

  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  @Test
  public void testThreadSafety() throws InterruptedException {
    WritableMutableContainer c1w = WritableContainer.createDefault().createWritableCopy();
    c1w.setLong1(UInt64.valueOf(0x1));
    c1w.setLong2(UInt64.valueOf(0x2));

    c1w.getSub1().setLong1(UInt64.valueOf(0x111));
    c1w.getSub1().setLong2(UInt64.valueOf(0x222));

    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x333)));
    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x444)));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UInt64.valueOf(0x555));
              sc.setLong2(UInt64.valueOf(0x666));
            });

    c1w.getVector1()
        .set(
            0,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));
    c1w.getVector1()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0xaaa), Bytes32.leftPad(Bytes.fromHexString("0xaaaa"))));

    WritableContainer c1r = c1w.commitChanges();

    // sanity check of equalsByGetters
    SszDataAssert.assertThatSszData(c1r)
        .isEqualTo(c1w)
        .isEqualByGettersTo(c1w)
        .isEqualByHashTreeRootTo(c1w)
        .isEqualBySszTo(c1w);
    WritableMutableContainer c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(0).setLong1(UInt64.valueOf(293874));
    SszDataAssert.assertThatSszData(c1r)
        .isEqualTo(c1w)
        .isEqualByGettersTo(c1w)
        .isEqualByHashTreeRootTo(c1w)
        .isEqualBySszTo(c1w);
    SszDataAssert.assertThatSszData(c1r).isNotEqualByAllMeansTo(c2w);
    SszDataAssert.assertThatSszData(c1r).isNotEqualByAllMeansTo(c2w.commitChanges());

    // new container from backing tree without any cached views
    WritableContainer c2r =
        WritableContainer.SSZ_SCHEMA.createFromBackingNode(c1r.getBackingNode());
    // concurrently traversing children of the the same view instance to make sure the internal
    // cache is thread safe
    List<Future<Boolean>> futures =
        TestUtil.executeParallel(() -> SszDataAssert.isEqualByGetters(c2r, c1r), 512);

    assertThat(TestUtil.waitAll(futures)).containsOnly(true);

    Consumer<WritableMutableContainer> containerMutator =
        w -> {
          w.setLong2(UInt64.valueOf(0x11111));
          w.getSub1().setLong2(UInt64.valueOf(0x22222));
          w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x44444)));
          w.getList1().set(0, SszUInt64.of(UInt64.fromLongBits(0x11111)));
          WritableMutableSubContainer sc = w.getList2().append();
          sc.setLong1(UInt64.valueOf(0x77777));
          sc.setLong2(UInt64.valueOf(0x88888));
          w.getList2().getByRef(0).setLong2(UInt64.valueOf(0x44444));
          w.getVector1()
              .set(
                  0,
                  new ImmutableSubContainerImpl(
                      UInt64.valueOf(0x99999), Bytes32.leftPad(Bytes.fromHexString("0xa99999"))));
        };
    WritableMutableContainer c3w = c1r.createWritableCopy();
    containerMutator.accept(c3w);
    WritableContainer c3r = c3w.commitChanges();

    WritableContainer c4r =
        WritableContainer.SSZ_SCHEMA.createFromBackingNode(c1r.getBackingNode());

    SszDataAssert.assertThatSszData(c4r).isEqualByAllMeansTo(c1r);
    // make updated view from the source view in parallel
    // this tests that mutable view caches are merged and transferred
    // in a thread safe way
    List<Future<WritableContainer>> modifiedFuts =
        TestUtil.executeParallel(
            () -> {
              WritableMutableContainer w = c4r.createWritableCopy();
              containerMutator.accept(w);
              return w.commitChanges();
            },
            512);

    List<WritableContainer> modified = TestUtil.waitAll(modifiedFuts);
    SszDataAssert.assertThatSszData(c4r).isEqualByAllMeansTo(c1r);

    assertThat(modified)
        .allSatisfy(c -> SszDataAssert.assertThatSszData(c).isEqualByAllMeansTo(c3r));
  }

  // ===== Progressive container helpers =====

  private static SszContainer createProgressiveAllFixed(
      final UInt64 a, final byte b, final UInt64 c) {
    final TreeNode tree =
        PROGRESSIVE_ALL_FIXED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(a), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(b), SszUInt64.of(c)));
    return PROGRESSIVE_ALL_FIXED_SCHEMA.createFromBackingNode(tree);
  }

  private static SszContainer createProgressiveMixed(
      final UInt64 fixedVal, final int bitlistSize, final int... setBits) {
    final TreeNode tree =
        PROGRESSIVE_MIXED_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(fixedVal), SszBitlistSchema.create(100).ofBits(bitlistSize, setBits)));
    return PROGRESSIVE_MIXED_SCHEMA.createFromBackingNode(tree);
  }

  private static SszContainer createProgressiveGapped(final UInt64 first, final byte third) {
    final TreeNode tree =
        PROGRESSIVE_GAPPED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(first), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(third)));
    return PROGRESSIVE_GAPPED_SCHEMA.createFromBackingNode(tree);
  }

  // ===== Progressive container immutable tests =====

  @Test
  void progressive_fieldAccess_allFixed() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    assertThat(((SszUInt64) container.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) container.get(1)).get()).isEqualTo((byte) 20);
    assertThat(((SszUInt64) container.get(2)).get()).isEqualTo(UInt64.valueOf(30));
  }

  @Test
  void progressive_fieldAccess_gapped() {
    final SszContainer container = createProgressiveGapped(UInt64.valueOf(99), (byte) 3);
    assertThat(((SszUInt64) container.get(0)).get()).isEqualTo(UInt64.valueOf(99));
    assertThat(((SszByte) container.get(1)).get()).isEqualTo((byte) 3);
  }

  @Test
  void progressive_createWritableCopy_shouldSucceed() {
    final SszContainer container = createProgressiveAllFixed(UInt64.ONE, (byte) 0, UInt64.ZERO);
    assertThat(container.createWritableCopy()).isNotNull();
  }

  @Test
  void progressive_isWritableSupported_shouldReturnTrue() {
    final SszContainer container = createProgressiveAllFixed(UInt64.ONE, (byte) 0, UInt64.ZERO);
    assertThat(container.isWritableSupported()).isTrue();
  }

  @Test
  void progressive_toString_shouldIncludeContainerNameAndFieldValues() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    final String str = container.toString();
    assertThat(str).contains("AllFixed");
    assertThat(str).contains("a=");
    assertThat(str).contains("b=");
    assertThat(str).contains("c=");
  }

  // ===== Progressive container mutable tests =====

  @Test
  void progressive_mutable_createWritableCopy_shouldSucceed() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();
    assertThat(mutable.size()).isEqualTo(3);
    assertThat(((SszUInt64) mutable.get(0)).get()).isEqualTo(UInt64.valueOf(10));
  }

  @Test
  void progressive_mutable_setAndGet_roundtrip() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));

    assertThat(((SszUInt64) mutable.get(0)).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(((SszByte) mutable.get(1)).get()).isEqualTo((byte) 2);
    assertThat(((SszUInt64) mutable.get(2)).get()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  void progressive_mutable_commitChanges_producesCorrectImmutable() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(1, SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 99));
    final SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.ONE);
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 99);
    assertThat(((SszUInt64) committed.get(2)).get()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  void progressive_mutable_commitChanges_hashTreeRootMatchesFreshCreation() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));
    mutable.set(2, SszUInt64.of(UInt64.valueOf(300)));
    final SszContainer committed = mutable.commitChanges();

    final SszContainer expected =
        createProgressiveAllFixed(UInt64.valueOf(100), (byte) 2, UInt64.valueOf(300));
    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void progressive_mutable_unchangedFields_remainUntouched() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(1, SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 99));
    final SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 99);
    assertThat(((SszUInt64) committed.get(2)).get()).isEqualTo(UInt64.valueOf(30));
  }

  @Test
  void progressive_mutable_gappedContainer_setAndCommit() {
    final SszContainer container = createProgressiveGapped(UInt64.valueOf(42), (byte) 7);
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));
    final SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 7);

    final SszContainer expected = createProgressiveGapped(UInt64.valueOf(100), (byte) 7);
    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void progressive_mutable_multipleCommits() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));

    final SszMutableContainer mutable1 = (SszMutableContainer) container.createWritableCopy();
    mutable1.set(0, SszUInt64.of(UInt64.valueOf(10)));
    final SszContainer committed1 = mutable1.commitChanges();

    final SszMutableContainer mutable2 = (SszMutableContainer) committed1.createWritableCopy();
    mutable2.set(2, SszUInt64.of(UInt64.valueOf(30)));
    final SszContainer committed2 = mutable2.commitChanges();

    assertThat(((SszUInt64) committed2.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) committed2.get(1)).get()).isEqualTo((byte) 2);
    assertThat(((SszUInt64) committed2.get(2)).get()).isEqualTo(UInt64.valueOf(30));

    final SszContainer expected =
        createProgressiveAllFixed(UInt64.valueOf(10), (byte) 2, UInt64.valueOf(30));
    assertThat(committed2.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void progressive_mutable_commitWithNoChanges_returnsSameData() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    final SszContainer committed = mutable.commitChanges();
    assertThat(committed.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
  }

  @Test
  void progressive_mutable_sszSerializationRoundtrip_afterMutation() {
    final SszContainer container =
        createProgressiveAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));
    final SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();
    mutable.set(0, SszUInt64.of(UInt64.valueOf(42)));
    final SszContainer committed = mutable.commitChanges();

    final Bytes serialized = committed.sszSerialize();
    final SszContainer deserialized = PROGRESSIVE_ALL_FIXED_SCHEMA.sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(committed.hashTreeRoot());
    assertThat(((SszUInt64) deserialized.get(0)).get()).isEqualTo(UInt64.valueOf(42));
  }
}
