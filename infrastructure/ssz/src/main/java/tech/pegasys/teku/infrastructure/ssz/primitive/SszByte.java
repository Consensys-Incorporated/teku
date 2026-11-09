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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;

public class SszByte extends AbstractSszPrimitive<Byte> {

  // There are only 256 possible byte values and SszByte is immutable (see AbstractSszPrimitive),
  // so all instances are interned. This removes the per-element SszByte (and boxed Byte) allocation
  // that dominated SSZ deserialization of byte collections. Two caches are needed because a SszByte
  // carries its schema: of(..) uses BYTE_SCHEMA, asUInt8(..) uses UINT8_SCHEMA.
  private static final SszByte[] BYTE_CACHE = new SszByte[256];
  private static final SszByte[] UINT8_CACHE = new SszByte[256];

  static {
    for (int i = 0; i < 256; i++) {
      BYTE_CACHE[i] = new SszByte((byte) i, SszPrimitiveSchemas.BYTE_SCHEMA);
      UINT8_CACHE[i] = new SszByte((byte) i, SszPrimitiveSchemas.UINT8_SCHEMA);
    }
  }

  public static final SszByte ZERO = BYTE_CACHE[0];

  public static SszByte of(final int value) {
    return BYTE_CACHE[value & 0xFF];
  }

  public static SszByte asUInt8(final byte value) {
    return UINT8_CACHE[value & 0xFF];
  }

  public static SszByte asUInt8(final int value) {
    checkArgument(value >= 0 && value <= 255, "value must be in uint8 range (0–255)");
    return UINT8_CACHE[value];
  }

  public static SszByte of(final byte value) {
    return BYTE_CACHE[value & 0xFF];
  }

  private SszByte(final Byte value, final AbstractSszPrimitiveSchema<Byte, SszByte> schema) {
    super(value, schema);
  }
}
