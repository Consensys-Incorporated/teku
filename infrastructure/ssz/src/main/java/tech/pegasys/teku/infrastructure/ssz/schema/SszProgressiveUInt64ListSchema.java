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

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszProgressiveUInt64ListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Progressive (EIP-7916) list of UInt64 values that preserves Teku's {@link SszUInt64List}
 * interface. Produces {@link SszProgressiveUInt64ListImpl} instances and uses progressive
 * merkleization with no fixed max capacity.
 */
public class SszProgressiveUInt64ListSchema
    extends AbstractSszProgressiveListSchema<SszUInt64, SszUInt64List>
    implements SszUInt64ListSchema<SszUInt64List> {

  private SszProgressiveUInt64ListSchema(final SszSchemaHints hints) {
    super(SszPrimitiveSchemas.UINT64_SCHEMA, hints);
  }

  public SszProgressiveUInt64ListSchema(final SszSchemaHints hints, final long maxLength) {
    super(SszPrimitiveSchemas.UINT64_SCHEMA, hints, maxLength);
  }

  public static SszProgressiveUInt64ListSchema create() {
    return create(SszSchemaHints.none());
  }

  public static SszProgressiveUInt64ListSchema create(final SszSchemaHints hints) {
    return new SszProgressiveUInt64ListSchema(hints);
  }

  /** A progressive uint64 list that additionally enforces {@code maxLength}. */
  public static SszProgressiveUInt64ListSchema create(final long maxLength) {
    return new SszProgressiveUInt64ListSchema(SszSchemaHints.none(), maxLength);
  }

  @Override
  public SszUInt64List createFromBackingNode(final TreeNode node) {
    return new SszProgressiveUInt64ListImpl(this, node);
  }
}
