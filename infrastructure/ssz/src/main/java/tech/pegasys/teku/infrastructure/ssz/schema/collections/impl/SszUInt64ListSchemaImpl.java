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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszUInt64ListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszUInt64ListSchemaImpl<SszListT extends SszUInt64List>
    extends SszPrimitiveListSchemaImpl<UInt64, SszUInt64, SszListT>
    implements SszUInt64ListSchema<SszListT> {

  public SszUInt64ListSchemaImpl(final long maxLength) {
    super(SszPrimitiveSchemas.UINT64_SCHEMA, maxLength);
  }

  public SszUInt64ListSchemaImpl(final long maxLength, final SszSchemaHints hints) {
    super(SszPrimitiveSchemas.UINT64_SCHEMA, maxLength, hints);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszUInt64ListImpl(this, node);
  }
}
