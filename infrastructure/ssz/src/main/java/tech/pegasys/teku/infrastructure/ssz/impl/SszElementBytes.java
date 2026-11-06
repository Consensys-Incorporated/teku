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

package tech.pegasys.teku.infrastructure.ssz.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteOrder;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszBooleanField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszByteVectorField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFixedField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUInt64Field;

/**
 * The serialized bytes of one list element, handed to the visitor of {@link
 * SszList#forEachElementSsz}: its fields are read by {@link SszFixedField} handles of the element's
 * schema, without creating views. {@code uint64} fields are returned as their raw bits: a value of
 * {@code 2^63} or more (such as {@code FAR_FUTURE_EPOCH}, all ones) is a negative {@code long}, so
 * compare them with {@link Long#compareUnsigned} or wrap them with {@code UInt64.fromLongBits}.
 * Byte vector fields are only handed out as {@code byte[]} copies.
 *
 * <p>The cursor is reused: the walk moves one instance from element to element, so it is valid only
 * during the visit and must never be stored or shared. When the walk ends it is invalidated and any
 * later read throws {@link IllegalStateException}. It is not a substitute for an immutable view: to
 * keep an element, read the fields needed or get the element view from the list.
 *
 * <p>Every read checks that the handle was created from the very schema instance of the list's
 * elements (one reference comparison), so a handle of another schema throws {@link
 * IllegalArgumentException} instead of reading bytes of a different layout.
 *
 * @param <E> the element view type
 */
public final class SszElementBytes<E extends SszData> {
  private final SszSchema<?> elementSchema;
  private Bytes data;
  private int offset;

  SszElementBytes(final SszSchema<?> elementSchema) {
    this.elementSchema = elementSchema;
  }

  /** Points this cursor at the element serialized at {@code offset} in {@code data}. */
  void moveTo(final Bytes data, final int offset) {
    this.data = data;
    this.offset = offset;
  }

  /** Ends the walk: any later read throws {@link IllegalStateException}. */
  void invalidate() {
    this.data = null;
  }

  /**
   * The raw bits of a {@code uint64} field: values of {@code 2^63} or more are negative, compare
   * them unsigned.
   */
  public long get(final SszUInt64Field<E> field) {
    return data.getLong(fieldOffset(field), ByteOrder.LITTLE_ENDIAN);
  }

  public boolean get(final SszBooleanField<E> field) {
    return data.get(fieldOffset(field)) != 0;
  }

  /** The byte at {@code index} of a byte vector field. */
  public byte getByte(final SszByteVectorField<E> field, final int index) {
    Objects.checkIndex(index, field.length());
    return data.get(fieldOffset(field) + index);
  }

  /**
   * A copy of a byte vector field, never a view over the element's backing bytes: wrap it with
   * {@code Bytes32.wrap}, {@code Bytes48.wrap} or similar as needed.
   */
  public byte[] copy(final SszByteVectorField<E> field) {
    final int at = fieldOffset(field);
    final Bytes bytes = data;
    final byte[] copy = new byte[field.length()];
    for (int i = 0; i < copy.length; i++) {
      copy[i] = bytes.get(at + i);
    }
    return copy;
  }

  // The very schema instance, unconditionally: one reference comparison per read, as a handle of
  // another schema instance for the same view class would otherwise read a different layout. The
  // message arguments are plain references, so nothing is formatted or allocated unless a check
  // fails, and this method stays small enough to inline in every read.
  @SuppressWarnings({"ReferenceComparison", "ReferenceEquality"})
  private int fieldOffset(final SszFixedField<E> field) {
    checkArgument(
        field.schema() == elementSchema,
        "Field %s was created from another instance of the schema %s than the list's elements",
        field,
        elementSchema);
    checkState(
        data != null,
        "The element bytes are only valid during the visit of SszList.forEachElementSsz");
    return offset + field.offset();
  }
}
