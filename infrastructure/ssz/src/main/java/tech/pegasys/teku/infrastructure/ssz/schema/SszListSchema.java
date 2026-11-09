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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszListSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;

public interface SszListSchema<ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    extends SszCollectionSchema<ElementDataT, SszListT> {

  // we need additional depth level for the length node
  long MAX_LIST_MAX_LENGTH = 1L << (GIndexUtil.MAX_DEPTH - 2);

  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> create(
          final SszSchema<ElementDataT> elementSchema, final long maxLength) {
    return create(elementSchema, maxLength, SszSchemaHints.none());
  }

  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> createProgressive(
          final SszSchema<ElementDataT> elementSchema) {
    return createProgressive(elementSchema, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> create(
          final SszSchema<ElementDataT> elementSchema,
          final long maxLength,
          final SszSchemaHints hints) {
    checkArgument(maxLength >= 0 && maxLength <= MAX_LIST_MAX_LENGTH);
    if (elementSchema instanceof SszPrimitiveSchema) {
      return (SszListSchema<ElementDataT, ?>)
          SszPrimitiveListSchema.create((SszPrimitiveSchema<?, ?>) elementSchema, maxLength, hints);
    } else {
      return new SszListSchemaImpl<>(elementSchema, maxLength, hints);
    }
  }

  @SuppressWarnings("unchecked")
  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> createProgressive(
          final SszSchema<ElementDataT> elementSchema, final SszSchemaHints hints) {
    if (elementSchema instanceof SszPrimitiveSchema) {
      return (SszListSchema<ElementDataT, ?>)
          SszPrimitiveListSchema.createProgressive((SszPrimitiveSchema<?, ?>) elementSchema, hints);
    } else {
      return SszProgressiveListSchema.create(elementSchema, hints);
    }
  }
}
