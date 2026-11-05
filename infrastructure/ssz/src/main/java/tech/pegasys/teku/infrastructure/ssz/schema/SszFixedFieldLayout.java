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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;

/** Resolves and checks the byte layout of the {@link SszFixedField} handles of a container. */
final class SszFixedFieldLayout {

  private SszFixedFieldLayout() {}

  static int uint64Offset(final SszContainerSchema<?> schema, final String fieldName) {
    return offset(
        schema,
        fieldName,
        SszPrimitiveSchemas.UINT64_SCHEMA::equals,
        SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  static int booleanOffset(final SszContainerSchema<?> schema, final String fieldName) {
    return offset(
        schema, fieldName, SszPrimitiveSchemas.BIT_SCHEMA::equals, SszPrimitiveSchemas.BIT_SCHEMA);
  }

  static int byteVectorOffset(
      final SszContainerSchema<?> schema, final String fieldName, final int length) {
    return offset(
        schema,
        fieldName,
        fieldSchema -> isByteVectorSchema(fieldSchema, length),
        "a byte vector of length " + length);
  }

  /** Checks that a handle was created with the offset its schema gives the field. */
  static void checkOffset(
      final SszContainerSchema<?> schema,
      final String fieldName,
      final int offset,
      final int expectedOffset) {
    checkArgument(
        offset == expectedOffset,
        "Field %s.%s is at offset %s, not %s",
        schema.getContainerName(),
        fieldName,
        expectedOffset,
        offset);
  }

  /**
   * The byte offset of the top-level field {@code fieldName} in the serialized form of the
   * fixed-size container {@code schema}: the sum of the fixed sizes of the fields before it.
   */
  private static int offset(
      final SszContainerSchema<?> schema,
      final String fieldName,
      final Predicate<SszSchema<?>> isExpectedSchema,
      final Object expectedSchema) {
    final String containerName = schema.getContainerName();
    checkArgument(schema.isFixedSize(), "Container %s is not fixed-size", containerName);
    final int fieldIndex = schema.getFieldIndex(fieldName);
    checkArgument(fieldIndex >= 0, "Container %s has no field %s", containerName, fieldName);
    final SszSchema<?> fieldSchema = schema.getChildSchema(fieldIndex);
    checkArgument(
        isExpectedSchema.test(fieldSchema),
        "Field %s.%s is %s, expected %s",
        containerName,
        fieldName,
        fieldSchema,
        expectedSchema);
    int offset = 0;
    for (int i = 0; i < fieldIndex; i++) {
      offset += schema.getChildSchema(i).getSszFixedPartSize();
    }
    return offset;
  }

  private static boolean isByteVectorSchema(final SszSchema<?> schema, final int length) {
    if (schema instanceof SszByteVectorSchema<?> byteVectorSchema) {
      return byteVectorSchema.getLength() == length;
    }
    return (schema.equals(SszPrimitiveSchemas.BYTES32_SCHEMA) && length == Bytes32.SIZE)
        || (schema.equals(SszPrimitiveSchemas.BYTES4_SCHEMA) && length == Bytes4.SIZE);
  }
}
