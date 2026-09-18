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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Progressive lists have no SSZ-level capacity, but a schema may carry a max length to enforce. */
class SszProgressiveListSchemaMaxLengthTest {

  private static final SszProgressiveListSchema<SszUInt64> UNLIMITED_UINT64_LIST =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
  private static final SszProgressiveListSchema<SszUInt64> LIMITED_UINT64_LIST =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2);

  private static final SszProgressiveByteListSchema<SszByteList> BYTE_LIST_SCHEMA =
      new SszProgressiveByteListSchema<>();
  private static final SszProgressiveListSchema<SszByteList> UNLIMITED_BYTE_LISTS =
      SszProgressiveListSchema.create(BYTE_LIST_SCHEMA);
  private static final SszProgressiveListSchema<SszByteList> LIMITED_BYTE_LISTS =
      SszProgressiveListSchema.create(BYTE_LIST_SCHEMA, 2);
  private static final SszProgressiveListSchema<SszByteList> LIMITED_PACKED_BYTE_LISTS =
      SszProgressiveListSchema.create(BYTE_LIST_SCHEMA, SszSchemaHints.sszPackedByteLists(), 2);

  @Test
  void getMaxLength_shouldReturnLimit() {
    assertThat(LIMITED_UINT64_LIST.getMaxLength()).isEqualTo(2);
    assertThat(UNLIMITED_UINT64_LIST.getMaxLength()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void sszDeserialize_shouldAcceptListAtLimit() {
    final Bytes ssz = uint64s(UNLIMITED_UINT64_LIST, 2).sszSerialize();
    assertThat(LIMITED_UINT64_LIST.sszDeserialize(ssz).size()).isEqualTo(2);
  }

  @Test
  void sszDeserialize_shouldRejectTooManyFixedSizeElements() {
    final Bytes ssz = uint64s(UNLIMITED_UINT64_LIST, 3).sszSerialize();
    assertThatThrownBy(() -> LIMITED_UINT64_LIST.sszDeserialize(ssz))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessageContaining("3")
        .hasMessageContaining("2");
  }

  @Test
  void sszDeserialize_shouldRejectTooManyVariableSizeElements() {
    final Bytes ssz = byteLists(UNLIMITED_BYTE_LISTS, 3).sszSerialize();
    assertThatThrownBy(() -> LIMITED_BYTE_LISTS.sszDeserialize(ssz))
        .isInstanceOf(SszDeserializeException.class);
    assertThat(LIMITED_BYTE_LISTS.sszDeserialize(byteLists(UNLIMITED_BYTE_LISTS, 2).sszSerialize()))
        .hasSize(2);
  }

  @Test
  void sszDeserialize_shouldRejectTooManyPackedByteListElements() {
    final Bytes ssz = byteLists(UNLIMITED_BYTE_LISTS, 3).sszSerialize();
    assertThatThrownBy(() -> LIMITED_PACKED_BYTE_LISTS.sszDeserialize(ssz))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("List length 3 exceeds max length 2");
    assertThat(
            LIMITED_PACKED_BYTE_LISTS.sszDeserialize(
                byteLists(UNLIMITED_BYTE_LISTS, 2).sszSerialize()))
        .hasSize(2);
  }

  @Test
  void sszDeserialize_shouldRejectBeforeMaterializingAnyElement() {
    final AtomicInteger elementDeserializations = new AtomicInteger();
    final SszContainerSchema<SszContainerImpl> countingElementSchema =
        new AbstractSszContainerSchema<>(
            List.of(SszPrimitiveSchemas.UINT64_SCHEMA, BYTE_LIST_SCHEMA)) {
          @Override
          public SszContainerImpl createFromBackingNode(final TreeNode node) {
            return new SszContainerImpl(this, node);
          }

          @Override
          public TreeNode sszDeserializeTree(final SszReader reader) {
            elementDeserializations.incrementAndGet();
            return super.sszDeserializeTree(reader);
          }
        };
    final SszProgressiveListSchema<SszContainerImpl> unlimited =
        SszProgressiveListSchema.create(countingElementSchema);
    final SszProgressiveListSchema<SszContainerImpl> limited =
        SszProgressiveListSchema.create(countingElementSchema, 2);
    final Bytes ssz =
        unlimited
            .createFromElements(
                IntStream.range(0, 3).mapToObj(__ -> countingElementSchema.getDefault()).toList())
            .sszSerialize();

    assertThatThrownBy(() -> limited.sszDeserialize(ssz))
        .isInstanceOf(SszDeserializeException.class);
    assertThat(elementDeserializations).hasValue(0);
  }

  @Test
  void uint64List_shouldSupportMaxLength() {
    final SszProgressiveUInt64ListSchema limited = SszProgressiveUInt64ListSchema.create(2);
    assertThat(limited.getMaxLength()).isEqualTo(2);
    final Bytes ssz = uint64s(UNLIMITED_UINT64_LIST, 3).sszSerialize();
    assertThatThrownBy(() -> limited.sszDeserialize(ssz))
        .isInstanceOf(SszDeserializeException.class);
    assertThat(limited).isNotEqualTo(SszProgressiveUInt64ListSchema.create());
  }

  @Test
  void createFromElements_shouldRejectTooManyElements() {
    final List<SszUInt64> three =
        IntStream.range(0, 3).mapToObj(i -> SszUInt64.of(UInt64.valueOf(i))).toList();
    assertThatThrownBy(() -> LIMITED_UINT64_LIST.createFromElements(three))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void writableCopy_shouldRejectAppendingBeyondMaxLength() {
    final SszMutableList<SszUInt64> writable = uint64s(LIMITED_UINT64_LIST, 2).createWritableCopy();
    assertThatThrownBy(() -> writable.append(SszUInt64.of(UInt64.valueOf(3))))
        .isInstanceOf(IndexOutOfBoundsException.class);
    writable.set(1, SszUInt64.of(UInt64.valueOf(9)));
    assertThat(writable.commitChanges().get(1).get()).isEqualTo(UInt64.valueOf(9));
  }

  @Test
  void getSszLengthBounds_shouldBeFiniteWhenLimited() {
    assertThat(LIMITED_UINT64_LIST.getSszLengthBounds().getMaxBytes()).isEqualTo(16);
    assertThat(UNLIMITED_UINT64_LIST.getSszLengthBounds().isUnbounded()).isTrue();
  }

  @Test
  void equals_shouldIncludeMaxLength() {
    assertThat(LIMITED_UINT64_LIST)
        .isEqualTo(SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2))
        .isNotEqualTo(UNLIMITED_UINT64_LIST)
        .isNotEqualTo(SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3));
    assertThat(LIMITED_UINT64_LIST.hashCode())
        .isEqualTo(
            SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2).hashCode());
  }

  private static SszList<SszUInt64> uint64s(
      final SszProgressiveListSchema<SszUInt64> schema, final int count) {
    return schema.createFromElements(
        IntStream.range(0, count).mapToObj(i -> SszUInt64.of(UInt64.valueOf(i))).toList());
  }

  private static SszList<SszByteList> byteLists(
      final SszProgressiveListSchema<SszByteList> schema, final int count) {
    return schema.createFromElements(
        IntStream.range(0, count)
            .mapToObj(i -> BYTE_LIST_SCHEMA.fromBytes(Bytes.of(i, i, i)))
            .toList());
  }
}
