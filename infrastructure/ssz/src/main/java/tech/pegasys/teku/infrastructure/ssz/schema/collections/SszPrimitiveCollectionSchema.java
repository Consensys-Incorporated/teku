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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveCollection;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;

public interface SszPrimitiveCollectionSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT>,
        SszCollectionT extends SszPrimitiveCollection<ElementT, SszElementT>>
    extends SszCollectionSchema<SszElementT, SszCollectionT> {

  @SuppressWarnings("unchecked")
  default SszCollectionT of(final ElementT... rawElements) {
    return of(Arrays.asList(rawElements));
  }

  default SszCollectionT of(final List<? extends ElementT> rawElements) {
    SszPrimitiveSchema<ElementT, SszElementT> elementSchema = getPrimitiveElementSchema();
    return createFromElements(rawElements.stream().map(elementSchema::boxed).toList());
  }

  @SuppressWarnings("unchecked")
  default SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return (SszPrimitiveSchema<ElementT, SszElementT>) getElementSchema();
  }

  default Collector<ElementT, ?, SszCollectionT> collectorUnboxed() {
    return Collectors.collectingAndThen(Collectors.<ElementT>toList(), this::of);
  }
}
