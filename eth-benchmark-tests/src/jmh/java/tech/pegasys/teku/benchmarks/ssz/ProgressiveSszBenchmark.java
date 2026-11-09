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

package tech.pegasys.teku.benchmarks.ssz;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xmx2g", "-Xms2g"})
public class ProgressiveSszBenchmark {

  private static final int RANDOM_ACCESS_COUNT = 1024;
  private static final int DEFAULT_LIST_SIZE = 65_536;
  private static final int DEFAULT_CONTAINER_FIELD_COUNT = 64;
  private static final int ELEMENT_FIELD_COUNT = 4;

  public enum Representation {
    BOUNDED,
    PROGRESSIVE
  }

  @State(Scope.Thread)
  public static class ByteListState {

    @Param({"BOUNDED", "PROGRESSIVE"})
    public Representation representation;

    @Param({"" + DEFAULT_LIST_SIZE})
    public int size;

    private SszByteListSchema<? extends SszByteList> schema;
    private TreeNode backingNode;
    private Bytes serialized;
    private int[] randomIndexes;

    @Setup
    public void setup() {
      schema =
          representation == Representation.BOUNDED
              ? SszByteListSchema.create(size + 1024L)
              : new SszProgressiveByteListSchema<>();

      final SszByteList list = schema.fromBytes(createBytes(size));
      backingNode = list.getBackingNode();
      serialized = list.sszSerialize();
      randomIndexes = createRandomIndexes(size);
    }

    private SszByteList createView() {
      return schema.createFromBackingNode(backingNode);
    }
  }

  /**
   * Tree nodes memoize their hashTreeRoot, so hashing benchmarks must rebuild an uncached tree
   * before every invocation; otherwise they only measure a cached-field read after the first call.
   */
  @State(Scope.Thread)
  public static class ByteListHashState {

    private SszByteList freshList;

    @Setup(Level.Invocation)
    public void setupInvocation(final ByteListState listState) {
      freshList = listState.schema.sszDeserialize(listState.serialized);
    }
  }

  @State(Scope.Thread)
  public static class ProgressiveByteListMutationState {

    @Param({"" + DEFAULT_LIST_SIZE})
    public int size;

    private SszByteList list;
    private SszByte replacementValue;
    private SszByte appendedValue;
    private int mutationIndex;

    @Setup
    public void setup() {
      final SszProgressiveByteListSchema<? extends SszByteList> schema =
          new SszProgressiveByteListSchema<>();
      list = schema.fromBytes(createBytes(size));
      replacementValue = SszByte.of(0xAB);
      appendedValue = SszByte.of(0xCD);
      mutationIndex = size / 2;
    }
  }

  @State(Scope.Thread)
  public static class BitlistState {

    @Param({"BOUNDED", "PROGRESSIVE"})
    public Representation representation;

    @Param({"" + DEFAULT_LIST_SIZE})
    public int size;

    private SszBitlistSchema<? extends SszBitlist> schema;
    private TreeNode backingNode;
    private Bytes serialized;
    private SszBitlist other;
    private int[] randomIndexes;

    @Setup
    public void setup() {
      schema =
          representation == Representation.BOUNDED
              ? SszBitlistSchema.create(size + 1024L)
              : new SszProgressiveBitlistSchema();

      final SszBitlist bitlist = schema.wrapBitSet(size, createBitSet(size, 17, 3));
      backingNode = bitlist.getBackingNode();
      serialized = bitlist.sszSerialize();
      other = schema.wrapBitSet(size, createBitSet(size, 19, 5));
      randomIndexes = createRandomIndexes(size);
    }

    private SszBitlist createView() {
      return schema.createFromBackingNode(backingNode);
    }
  }

  /** See {@link ByteListHashState} for why the tree is rebuilt per invocation. */
  @State(Scope.Thread)
  public static class BitlistHashState {

    private SszBitlist freshList;

    @Setup(Level.Invocation)
    public void setupInvocation(final BitlistState bitlistState) {
      freshList = bitlistState.schema.sszDeserialize(bitlistState.serialized);
    }
  }

  @State(Scope.Thread)
  public static class ProgressiveBitlistMutationState {

    @Param({"" + DEFAULT_LIST_SIZE})
    public int size;

    private SszBitlist list;
    private int mutationIndex;

    @Setup
    public void setup() {
      final SszProgressiveBitlistSchema schema = new SszProgressiveBitlistSchema();
      list = schema.wrapBitSet(size, createBitSet(size, 17, 3));
      mutationIndex = size / 2;
    }
  }

  @State(Scope.Thread)
  public static class NonPrimitiveListState {

    @Param({"BOUNDED", "PROGRESSIVE"})
    public Representation representation;

    @Param({"4096"})
    public int size;

    private SszListSchema<SszContainer, ? extends SszList<SszContainer>> schema;
    private TreeNode backingNode;
    private Bytes serialized;
    private SszContainer replacementElement;
    private SszContainer appendedElement;
    private int mutationIndex;
    private int[] randomIndexes;

    @Setup
    public void setup() {
      final SszContainerSchema<SszContainer> elementSchema =
          createRegularContainerSchema("BenchmarkElement", ELEMENT_FIELD_COUNT);
      schema =
          representation == Representation.BOUNDED
              ? SszListSchema.create(elementSchema, size + 1024L)
              : SszListSchema.createProgressive(elementSchema);

      final List<SszContainer> elements =
          IntStream.range(0, size)
              .mapToObj(i -> createContainer(elementSchema, 10_000L + i * 100L))
              .toList();
      final SszList<SszContainer> list = schema.createFromElements(elements);

      backingNode = list.getBackingNode();
      serialized = list.sszSerialize();
      replacementElement = createContainer(elementSchema, 700_000L);
      appendedElement = createContainer(elementSchema, 800_000L);
      mutationIndex = size / 2;
      randomIndexes = createRandomIndexes(size);
    }

    private SszList<SszContainer> createView() {
      return schema.createFromBackingNode(backingNode);
    }
  }

  /** See {@link ByteListHashState} for why the tree is rebuilt per invocation. */
  @State(Scope.Thread)
  public static class NonPrimitiveListHashState {

    private SszList<SszContainer> freshList;

    @Setup(Level.Invocation)
    public void setupInvocation(final NonPrimitiveListState listState) {
      freshList = listState.schema.sszDeserialize(listState.serialized);
    }
  }

  @State(Scope.Thread)
  public static class ContainerState {

    @Param({"BOUNDED", "PROGRESSIVE"})
    public Representation representation;

    @Param({"" + DEFAULT_CONTAINER_FIELD_COUNT})
    public int fieldCount;

    private SszContainerSchema<SszContainer> schema;
    private TreeNode backingNode;
    private Bytes serialized;
    private SszUInt64 replacementValue;
    private int mutationIndex;
    private int[] randomIndexes;

    @Setup
    public void setup() {
      schema =
          representation == Representation.BOUNDED
              ? createRegularContainerSchema("BenchmarkContainer", fieldCount)
              : createProgressiveContainerSchema("BenchmarkProgressiveContainer", fieldCount);

      final SszContainer container = createContainer(schema, 900_000L);
      backingNode = container.getBackingNode();
      serialized = container.sszSerialize();
      replacementValue = SszUInt64.of(UInt64.valueOf(1_234_567L));
      mutationIndex = fieldCount / 2;
      randomIndexes = createRandomIndexes(fieldCount);
    }

    private SszContainer createView() {
      return schema.createFromBackingNode(backingNode);
    }
  }

  /** See {@link ByteListHashState} for why the tree is rebuilt per invocation. */
  @State(Scope.Thread)
  public static class ContainerHashState {

    private SszContainer freshContainer;

    @Setup(Level.Invocation)
    public void setupInvocation(final ContainerState containerState) {
      freshContainer = containerState.schema.sszDeserialize(containerState.serialized);
    }
  }

  @Benchmark
  public void byteListCreateView(final ByteListState state, final Blackhole bh) {
    bh.consume(state.createView());
  }

  @Benchmark
  public void byteListSequentialAccess(final ByteListState state, final Blackhole bh) {
    final SszByteList list = state.createView();
    final int size = list.size();
    for (int i = 0; i < size; i++) {
      bh.consume(list.getElement(i));
    }
  }

  @Benchmark
  public void byteListRandomAccess(final ByteListState state, final Blackhole bh) {
    final SszByteList list = state.createView();
    for (int index : state.randomIndexes) {
      bh.consume(list.getElement(index));
    }
  }

  @Benchmark
  public void byteListSerialize(final ByteListState state, final Blackhole bh) {
    bh.consume(state.createView().sszSerialize());
  }

  @Benchmark
  public void byteListDeserialize(final ByteListState state, final Blackhole bh) {
    bh.consume(state.schema.sszDeserialize(state.serialized));
  }

  @Benchmark
  public void byteListHashTreeRoot(final ByteListHashState state, final Blackhole bh) {
    bh.consume(state.freshList.hashTreeRoot());
  }

  @Benchmark
  public void progressiveByteListSetAndCommit(
      final ProgressiveByteListMutationState state, final Blackhole bh) {
    final SszMutablePrimitiveList<Byte, SszByte> writable = state.list.createWritableCopy();
    writable.set(state.mutationIndex, state.replacementValue);
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void progressiveByteListSetCommitAndHash(
      final ProgressiveByteListMutationState state, final Blackhole bh) {
    final SszMutablePrimitiveList<Byte, SszByte> writable = state.list.createWritableCopy();
    writable.set(state.mutationIndex, state.replacementValue);
    bh.consume(writable.commitChanges().hashTreeRoot());
  }

  @Benchmark
  public void progressiveByteListAppendAndCommit(
      final ProgressiveByteListMutationState state, final Blackhole bh) {
    final SszMutablePrimitiveList<Byte, SszByte> writable = state.list.createWritableCopy();
    writable.append(state.appendedValue);
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void bitlistCreateView(final BitlistState state, final Blackhole bh) {
    bh.consume(state.createView());
  }

  @Benchmark
  public void bitlistSequentialAccess(final BitlistState state, final Blackhole bh) {
    final SszBitlist bitlist = state.createView();
    final int size = bitlist.size();
    for (int i = 0; i < size; i++) {
      bh.consume(bitlist.getBit(i));
    }
  }

  @Benchmark
  public void bitlistRandomAccess(final BitlistState state, final Blackhole bh) {
    final SszBitlist bitlist = state.createView();
    for (int index : state.randomIndexes) {
      bh.consume(bitlist.getBit(index));
    }
  }

  @Benchmark
  public void bitlistOr(final BitlistState state, final Blackhole bh) {
    bh.consume(state.createView().or(state.other));
  }

  @Benchmark
  public void bitlistSerialize(final BitlistState state, final Blackhole bh) {
    bh.consume(state.createView().sszSerialize());
  }

  @Benchmark
  public void bitlistDeserialize(final BitlistState state, final Blackhole bh) {
    bh.consume(state.schema.sszDeserialize(state.serialized));
  }

  @Benchmark
  public void bitlistHashTreeRoot(final BitlistHashState state, final Blackhole bh) {
    bh.consume(state.freshList.hashTreeRoot());
  }

  @Benchmark
  public void progressiveBitlistSetAndCommit(
      final ProgressiveBitlistMutationState state, final Blackhole bh) {
    final SszMutablePrimitiveList<Boolean, SszBit> writable = state.list.createWritableCopy();
    writable.set(state.mutationIndex, SszBit.of(true));
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void progressiveBitlistAppendAndCommit(
      final ProgressiveBitlistMutationState state, final Blackhole bh) {
    final SszMutablePrimitiveList<Boolean, SszBit> writable = state.list.createWritableCopy();
    writable.append(SszBit.of(true));
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void nonPrimitiveListCreateView(final NonPrimitiveListState state, final Blackhole bh) {
    bh.consume(state.createView());
  }

  @Benchmark
  public void nonPrimitiveListSequentialAccess(
      final NonPrimitiveListState state, final Blackhole bh) {
    final SszList<SszContainer> list = state.createView();
    final int size = list.size();
    for (int i = 0; i < size; i++) {
      bh.consume(list.get(i));
    }
  }

  @Benchmark
  public void nonPrimitiveListRandomAccess(final NonPrimitiveListState state, final Blackhole bh) {
    final SszList<SszContainer> list = state.createView();
    for (int index : state.randomIndexes) {
      bh.consume(list.get(index));
    }
  }

  @Benchmark
  public void nonPrimitiveListSerialize(final NonPrimitiveListState state, final Blackhole bh) {
    bh.consume(state.createView().sszSerialize());
  }

  @Benchmark
  public void nonPrimitiveListDeserialize(final NonPrimitiveListState state, final Blackhole bh) {
    bh.consume(state.schema.sszDeserialize(state.serialized));
  }

  @Benchmark
  public void nonPrimitiveListHashTreeRoot(
      final NonPrimitiveListHashState state, final Blackhole bh) {
    bh.consume(state.freshList.hashTreeRoot());
  }

  @Benchmark
  public void nonPrimitiveListSetAndCommit(final NonPrimitiveListState state, final Blackhole bh) {
    final SszMutableList<SszContainer> writable = state.createView().createWritableCopy();
    writable.set(state.mutationIndex, state.replacementElement);
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void nonPrimitiveListSetCommitAndHash(
      final NonPrimitiveListState state, final Blackhole bh) {
    final SszMutableList<SszContainer> writable = state.createView().createWritableCopy();
    writable.set(state.mutationIndex, state.replacementElement);
    bh.consume(writable.commitChanges().hashTreeRoot());
  }

  @Benchmark
  public void nonPrimitiveListAppendAndCommit(
      final NonPrimitiveListState state, final Blackhole bh) {
    final SszMutableList<SszContainer> writable = state.createView().createWritableCopy();
    writable.append(state.appendedElement);
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void containerCreateView(final ContainerState state, final Blackhole bh) {
    bh.consume(state.createView());
  }

  @Benchmark
  public void containerSequentialAccess(final ContainerState state, final Blackhole bh) {
    final SszContainer container = state.createView();
    final int size = container.size();
    for (int i = 0; i < size; i++) {
      bh.consume(container.get(i));
    }
  }

  @Benchmark
  public void containerRandomAccess(final ContainerState state, final Blackhole bh) {
    final SszContainer container = state.createView();
    for (int index : state.randomIndexes) {
      bh.consume(container.get(index));
    }
  }

  @Benchmark
  public void containerSerialize(final ContainerState state, final Blackhole bh) {
    bh.consume(state.createView().sszSerialize());
  }

  @Benchmark
  public void containerDeserialize(final ContainerState state, final Blackhole bh) {
    bh.consume(state.schema.sszDeserialize(state.serialized));
  }

  @Benchmark
  public void containerHashTreeRoot(final ContainerHashState state, final Blackhole bh) {
    bh.consume(state.freshContainer.hashTreeRoot());
  }

  @Benchmark
  public void containerSetAndCommit(final ContainerState state, final Blackhole bh) {
    final SszMutableContainer writable =
        (SszMutableContainer) state.createView().createWritableCopy();
    writable.set(state.mutationIndex, state.replacementValue);
    bh.consume(writable.commitChanges());
  }

  @Benchmark
  public void containerSetCommitAndHash(final ContainerState state, final Blackhole bh) {
    final SszMutableContainer writable =
        (SszMutableContainer) state.createView().createWritableCopy();
    writable.set(state.mutationIndex, state.replacementValue);
    bh.consume(writable.commitChanges().hashTreeRoot());
  }

  private static int[] createRandomIndexes(final int size) {
    final int count = Math.min(size, RANDOM_ACCESS_COUNT);
    final int[] indexes = new int[count];
    for (int i = 0; i < count; i++) {
      indexes[i] = Math.floorMod(i * 8191 + 17, size);
    }
    return indexes;
  }

  private static Bytes createBytes(final int size) {
    final byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) (17 + i * 31);
    }
    return Bytes.wrap(bytes);
  }

  private static BitSet createBitSet(final int size, final int stride, final int offset) {
    final BitSet bitSet = new BitSet(size);
    for (int i = offset; i < size; i += stride) {
      bitSet.set(i);
    }
    return bitSet;
  }

  private static SszContainerSchema<SszContainer> createRegularContainerSchema(
      final String name, final int fieldCount) {
    return SszContainerSchema.create(
        name, createNamedUInt64Fields(fieldCount), SszContainerImpl::new);
  }

  private static SszContainerSchema<SszContainer> createProgressiveContainerSchema(
      final String name, final int fieldCount) {
    final boolean[] activeFields = new boolean[fieldCount];
    Arrays.fill(activeFields, true);
    return SszContainerSchema.createProgressive(
        name, activeFields, createNamedUInt64Fields(fieldCount));
  }

  private static List<NamedSchema<?>> createNamedUInt64Fields(final int fieldCount) {
    return IntStream.range(0, fieldCount)
        .<NamedSchema<?>>mapToObj(
            i -> NamedSchema.of("field" + i, SszPrimitiveSchemas.UINT64_SCHEMA))
        .toList();
  }

  private static SszContainer createContainer(
      final SszContainerSchema<SszContainer> schema, final long seed) {
    final List<? extends SszData> fieldValues =
        IntStream.range(0, schema.getFieldsCount())
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(seed + i)))
            .toList();
    return schema.createFromFieldValues(fieldValues);
  }
}
