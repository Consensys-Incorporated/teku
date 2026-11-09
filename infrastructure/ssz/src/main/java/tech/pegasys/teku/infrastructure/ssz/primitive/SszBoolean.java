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
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;

/** Byte [0x00, 0x01] by spec, not a bit */
public class SszBoolean extends AbstractSszPrimitive<Boolean> {

  public static final SszBoolean ZERO = SszBoolean.of(false);

  public static SszBoolean of(final Boolean value) {
    return new SszBoolean(value);
  }

  private SszBoolean(final Boolean value) {
    this(value, SszPrimitiveSchemas.BOOLEAN_SCHEMA);
  }

  private SszBoolean(
      final Boolean value, final AbstractSszPrimitiveSchema<Boolean, SszBoolean> schema) {
    super(value, schema);
  }
}
