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
 * A fixed-length byte vector field ({@code Bytes32}, {@code Bytes48}, ...), only read as copies.
 * See {@link SszContainerSchema#byteVectorField(String, int)}.
 */
public record SszByteVectorField<E extends SszData>(
    SszContainerSchema<? extends E> schema, String name, int offset, int length)
    implements SszFixedField<E> {

  public SszByteVectorField(
      final SszContainerSchema<? extends E> schema,
      final String name,
      final int offset,
      final int length) {
    SszFixedFieldLayout.checkOffset(
        schema, name, offset, SszFixedFieldLayout.byteVectorOffset(schema, name, length));
    this.schema = schema;
    this.name = name;
    this.offset = offset;
    this.length = length;
  }

  public SszByteVectorField(
      final SszContainerSchema<? extends E> schema, final String name, final int length) {
    this(schema, name, SszFixedFieldLayout.byteVectorOffset(schema, name, length), length);
  }

  @Override
  public String toString() {
    return schema.getContainerName() + "." + name;
  }
}
