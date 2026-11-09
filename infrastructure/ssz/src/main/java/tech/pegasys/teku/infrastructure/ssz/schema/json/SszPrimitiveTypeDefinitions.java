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

package tech.pegasys.teku.infrastructure.ssz.schema.json;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.BooleanTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedPrimitiveTypeDefinition.StringTypeBuilder;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class SszPrimitiveTypeDefinitions {
  public static final DeserializableTypeDefinition<SszNone> SSZ_NONE_TYPE_DEFINITION =
      DeserializableTypeDefinition.string(SszNone.class)
          .formatter(Objects::toString)
          .parser(
              value -> {
                checkArgument(value == null, "SszNone must have null value");
                return null;
              })
          .example("null")
          .description("null value")
          .format("null")
          .build();

  public static final DeserializableTypeDefinition<SszBit> SSZ_BIT_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.BIT_SCHEMA, new BooleanTypeDefinition());

  public static final DeserializableTypeDefinition<SszByte> SSZ_BYTE_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.BYTE_SCHEMA, CoreTypes.BYTE_TYPE);

  public static final DeserializableTypeDefinition<SszBoolean> SSZ_BOOLEAN_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(
          SszPrimitiveSchemas.BOOLEAN_SCHEMA, CoreTypes.BOOLEAN_PRIMITIVE_TYPE);

  public static final DeserializableTypeDefinition<SszByte> SSZ_UINT8_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.UINT8_SCHEMA, CoreTypes.UINT8_TYPE);

  public static final DeserializableTypeDefinition<SszBytes32> SSZ_BYTES32_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.BYTES32_SCHEMA, CoreTypes.BYTES32_TYPE);

  public static final DeserializableTypeDefinition<SszBytes4> SSZ_BYTES4_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.BYTES4_SCHEMA, CoreTypes.BYTES4_TYPE);

  public static final DeserializableTypeDefinition<SszUInt256> SSZ_UINT256_TYPE_DEFINITION =
      new SszTypeDefinitionWrapper<>(SszPrimitiveSchemas.UINT256_SCHEMA, CoreTypes.UINT256_TYPE);

  public static <T extends SszUInt64> DeserializableTypeDefinition<T> createUInt64Definition(
      final SszPrimitiveSchema<UInt64, T> schema) {
    return new SszTypeDefinitionWrapper<>(schema, CoreTypes.UINT64_TYPE);
  }

  public static <T extends SszData> DeserializableTypeDefinition<T> sszSerializedType(
      final SszSchema<T> schema, final String description) {
    return new StringTypeBuilder<T>()
        .formatter(value -> value.sszSerialize().toHexString())
        .parser(value -> schema.sszDeserialize(Bytes.fromHexString(value)))
        .format("bytes")
        .pattern("^0x[a-fA-F0-9]{2,}$")
        .description(description)
        .build();
  }
}
