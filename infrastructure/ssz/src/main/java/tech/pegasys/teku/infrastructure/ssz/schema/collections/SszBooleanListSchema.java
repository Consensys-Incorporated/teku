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

import tech.pegasys.teku.infrastructure.ssz.collections.SszBooleanList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBooleanListSchemaImpl;

public interface SszBooleanListSchema<SszListT extends SszBooleanList>
    extends SszPrimitiveListSchema<Boolean, SszBoolean, SszListT> {

  static SszBooleanListSchema<SszBooleanList> create(final long maxLength) {
    return new SszBooleanListSchemaImpl<>(maxLength);
  }
}
