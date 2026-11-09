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

package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;

public class SszBit extends AbstractSszPrimitive<Boolean> {

  private static final SszBit TRUE_VIEW = new SszBit(true);
  private static final SszBit FALSE_VIEW = new SszBit(false);

  public static SszBit of(final boolean value) {
    return value ? TRUE_VIEW : FALSE_VIEW;
  }

  private SszBit(final Boolean value) {
    super(value, SszPrimitiveSchemas.BIT_SCHEMA);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public String toString() {
    return this == TRUE_VIEW ? "1" : "0";
  }
}
