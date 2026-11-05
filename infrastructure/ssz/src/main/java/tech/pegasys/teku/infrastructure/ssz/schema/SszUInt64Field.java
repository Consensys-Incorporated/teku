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

import tech.pegasys.teku.infrastructure.ssz.SszData;

/**
 * A {@code uint64} field, read as its raw bits: values of {@code 2^63} or more (such as {@code
 * FAR_FUTURE_EPOCH}, all ones) are negative {@code long}s, so compare them with {@link
 * Long#compareUnsigned} or wrap them with {@code UInt64.fromLongBits}. See {@link
 * SszContainerSchema#uint64Field(String)}.
 */
public record SszUInt64Field<E extends SszData>(
    SszContainerSchema<? extends E> schema, String name, int offset) implements SszFixedField<E> {

  public SszUInt64Field(
      final SszContainerSchema<? extends E> schema, final String name, final int offset) {
    SszFixedFieldLayout.checkOffset(
        schema, name, offset, SszFixedFieldLayout.uint64Offset(schema, name));
    this.schema = schema;
    this.name = name;
    this.offset = offset;
  }

  public SszUInt64Field(final SszContainerSchema<? extends E> schema, final String name) {
    this(schema, name, SszFixedFieldLayout.uint64Offset(schema, name));
  }

  @Override
  public int length() {
    return Long.BYTES;
  }

  @Override
  public String toString() {
    return schema.getContainerName() + "." + name;
  }
}
