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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszElementBytes;

/**
 * A handle on one top-level fixed-size field of a fixed-size container, created by its {@link
 * SszContainerSchema}: it resolves the field's byte offset and length in the container's serialized
 * form once, checked against the schema, so the field can be read from the {@link SszElementBytes}
 * handed out by {@link SszList#forEachElementSsz} without creating a view. The type parameter ties
 * the handle to the container's view type: a handle can only be built from a schema of that view
 * type, so it compiles only against a cursor of a list of such elements, and every read checks that
 * it was created from the very schema instance of the list's elements.
 *
 * <p>The handles are immutable records, meant to be held in {@code static final} constants next to
 * the schema. Every constructor validates the offset against the schema, so a handle cannot be
 * built with a layout its schema does not have.
 *
 * @param <E> the container view type
 */
public sealed interface SszFixedField<E extends SszData>
    permits SszUInt64Field, SszBooleanField, SszByteVectorField {

  /** The schema this handle was created from. */
  SszContainerSchema<? extends E> schema();

  String name();

  /** The field's byte offset in the container's serialized form. */
  int offset();

  /** The field's serialized length in bytes. */
  int length();
}
