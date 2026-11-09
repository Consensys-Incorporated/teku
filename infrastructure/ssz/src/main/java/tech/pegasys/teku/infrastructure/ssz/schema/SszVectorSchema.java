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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;

public interface SszVectorSchema<
        ElementDataT extends SszData, SszVectorT extends SszVector<ElementDataT>>
    extends SszCollectionSchema<ElementDataT, SszVectorT> {

  long MAX_VECTOR_LENGTH = 1L << (GIndexUtil.MAX_DEPTH - 1);

  default int getLength() {
    long maxLength = getMaxLength();
    if (maxLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxLength);
    }
    return (int) maxLength;
  }

  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      final SszSchema<ElementDataT> elementSchema, final long length) {
    return create(elementSchema, length, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      final SszSchema<ElementDataT> elementSchema, final long length, final SszSchemaHints hints) {
    checkArgument(length > 0 && length <= MAX_VECTOR_LENGTH);
    if (elementSchema instanceof SszPrimitiveSchema) {
      return (SszVectorSchema<ElementDataT, ?>)
          SszPrimitiveVectorSchema.create((SszPrimitiveSchema<?, ?>) elementSchema, length, hints);
    } else {
      return new SszVectorSchemaImpl<>(elementSchema, length, false, hints);
    }
  }
}
