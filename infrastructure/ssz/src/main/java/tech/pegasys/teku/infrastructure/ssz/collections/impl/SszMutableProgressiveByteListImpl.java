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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszMutableProgressiveListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Mutable progressive list that additionally implements {@link SszMutablePrimitiveList} for bytes,
 * so progressive byte-list fields (e.g. Gloas {@code participation}) can be mutated through a
 * mutable container. Mirrors {@link SszMutableProgressiveUInt64ListImpl}.
 */
public class SszMutableProgressiveByteListImpl
    extends SszMutableProgressiveListImpl<SszByte, SszByte>
    implements SszMutablePrimitiveList<Byte, SszByte> {

  public SszMutableProgressiveByteListImpl(final SszProgressiveByteListImpl backingImmutableList) {
    super(backingImmutableList);
  }

  @Override
  public void set(final int index, final SszByte value) {
    super.set(index, getElementSchema().boxed(value.get()));
  }

  @Override
  public SszByteList commitChanges() {
    return (SszByteList) super.commitChanges();
  }

  @Override
  protected SszProgressiveByteListImpl createImmutableSszComposite(
      final TreeNode backingNode, final IntCache<SszByte> childrenCache) {
    return new SszProgressiveByteListImpl(
        (SszProgressiveByteListSchema<?>) getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszMutablePrimitiveList<Byte, SszByte> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }

  @SuppressWarnings("unchecked")
  private SszPrimitiveSchema<Byte, SszByte> getElementSchema() {
    return (SszPrimitiveSchema<Byte, SszByte>) getSchema().getElementSchema();
  }
}
