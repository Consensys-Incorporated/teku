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

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/** Handy base class for immutable containers */
public abstract class AbstractSszImmutableContainer extends SszContainerImpl {

  protected AbstractSszImmutableContainer(
      final AbstractSszContainerSchema<? extends AbstractSszImmutableContainer> schema) {
    this(schema, schema.getDefaultTree());
  }

  protected AbstractSszImmutableContainer(
      final SszContainerSchema<? extends AbstractSszImmutableContainer> schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected AbstractSszImmutableContainer(
      final SszContainerSchema<? extends AbstractSszImmutableContainer> schema,
      final SszData... memberValues) {
    super(
        schema,
        schema.createTreeFromFieldValues(Arrays.asList(memberValues)),
        createCache(memberValues));
    checkArgument(
        memberValues.length == this.getSchema().getMaxLength(),
        "Wrong number of member values: %s",
        memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      Preconditions.checkArgument(
          memberValues[i].getSchema().equals(schema.getChildSchema(i)),
          "Wrong child schema at index %s. Expected: %s, was %s",
          i,
          schema.getChildSchema(i),
          memberValues[i].getSchema());
    }
  }

  private static IntCache<SszData> createCache(final SszData... memberValues) {
    ArrayIntCache<SszData> cache = new ArrayIntCache<>(memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      cache.invalidateWithNewValue(i, memberValues[i]);
    }
    return cache;
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException(
        "This container doesn't support mutable structure: " + getClass().getName());
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public boolean equals(final Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (obj instanceof final AbstractSszImmutableContainer other) {

      return hashTreeRoot().equals(other.hashTreeRoot());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(0, 4).toInt();
  }
}
