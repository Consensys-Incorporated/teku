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

/** A {@code boolean} (bit) field. See {@link SszContainerSchema#booleanField(String)}. */
public record SszBooleanField<E extends SszData>(
    SszContainerSchema<? extends E> schema, String name, int offset) implements SszFixedField<E> {

  public SszBooleanField(
      final SszContainerSchema<? extends E> schema, final String name, final int offset) {
    SszFixedFieldLayout.checkOffset(
        schema, name, offset, SszFixedFieldLayout.booleanOffset(schema, name));
    this.schema = schema;
    this.name = name;
    this.offset = offset;
  }

  public SszBooleanField(final SszContainerSchema<? extends E> schema, final String name) {
    this(schema, name, SszFixedFieldLayout.booleanOffset(schema, name));
  }

  @Override
  public int length() {
    return 1;
  }

  @Override
  public String toString() {
    return schema.getContainerName() + "." + name;
  }
}
