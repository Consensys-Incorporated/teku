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

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszPrimitiveVectorSchemaImpl;

public interface SszPrimitiveVectorSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT>,
        SszVectorT extends SszPrimitiveVector<ElementT, SszElementT>>
    extends SszPrimitiveCollectionSchema<ElementT, SszElementT, SszVectorT>,
        SszVectorSchema<SszElementT, SszVectorT> {

  static <ElementT, SszElementT extends SszPrimitive<ElementT>>
      SszPrimitiveVectorSchema<ElementT, SszElementT, ?> create(
          final SszPrimitiveSchema<ElementT, SszElementT> elementSchema, final int length) {
    return create(elementSchema, length, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <PrimT, SszPrimT extends SszPrimitive<PrimT>>
      SszPrimitiveVectorSchema<PrimT, SszPrimT, ?> create(
          final SszPrimitiveSchema<PrimT, SszPrimT> elementSchema,
          final long length,
          final SszSchemaHints hints) {
    if (elementSchema.equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>) SszBitvectorSchema.create(length);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>)
          SszByteVectorSchema.create((int) length);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT8_SCHEMA)) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>)
          SszByteVectorSchema.createUInt8((int) length);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BOOLEAN_SCHEMA)) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>) SszBooleanVectorSchema.create(length);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BYTES32_SCHEMA)) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>)
          SszBytes32VectorSchema.create((int) length);
    } else {
      return new SszPrimitiveVectorSchemaImpl<>(elementSchema, length);
    }
  }
}
