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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszPrimitiveListSchemaImpl;

public interface SszPrimitiveListSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT>,
        SszListT extends SszPrimitiveList<ElementT, SszElementT>>
    extends SszListSchema<SszElementT, SszListT>,
        SszPrimitiveCollectionSchema<ElementT, SszElementT, SszListT> {

  static <ElementT, SszElementT extends SszPrimitive<ElementT>>
      SszPrimitiveListSchema<ElementT, SszElementT, ?> create(
          final SszPrimitiveSchema<ElementT, SszElementT> elementSchema, final int maxLength) {
    return create(elementSchema, maxLength, SszSchemaHints.none());
  }

  static <ElementT, SszElementT extends SszPrimitive<ElementT>>
      SszListSchema<SszElementT, ? extends SszList<SszElementT>> createProgressive(
          final SszPrimitiveSchema<ElementT, SszElementT> elementSchema) {
    return createProgressive(elementSchema, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <PrimT, SszPrimT extends SszPrimitive<PrimT>>
      SszPrimitiveListSchema<PrimT, SszPrimT, ?> create(
          final SszPrimitiveSchema<PrimT, SszPrimT> elementSchema,
          final long maxLength,
          final SszSchemaHints hints) {
    if (elementSchema.equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszBitlistSchema.create(maxLength);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT64_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>)
          SszUInt64ListSchema.create(maxLength, hints);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>)
          SszByteListSchema.create(maxLength, hints);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT8_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>)
          SszByteListSchema.createUInt8(maxLength, hints);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BOOLEAN_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszBooleanListSchema.create(maxLength);
    } else {
      return new SszPrimitiveListSchemaImpl<>(elementSchema, maxLength, hints);
    }
  }

  @SuppressWarnings("unchecked")
  static <PrimT, SszPrimT extends SszPrimitive<PrimT>>
      SszListSchema<SszPrimT, ? extends SszList<SszPrimT>> createProgressive(
          final SszPrimitiveSchema<PrimT, SszPrimT> elementSchema, final SszSchemaHints hints) {
    if (elementSchema.equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      return (SszListSchema<SszPrimT, ?>) new SszProgressiveBitlistSchema();
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT64_SCHEMA)) {
      return (SszListSchema<SszPrimT, ?>) SszProgressiveUInt64ListSchema.create(hints);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)) {
      return (SszListSchema<SszPrimT, ?>)
          new SszProgressiveByteListSchema<>(SszPrimitiveSchemas.BYTE_SCHEMA, hints);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT8_SCHEMA)) {
      return (SszListSchema<SszPrimT, ?>)
          new SszProgressiveByteListSchema<>(SszPrimitiveSchemas.UINT8_SCHEMA, hints);
    } else {
      return new SszProgressiveListSchema<>(elementSchema, hints);
    }
  }
}
