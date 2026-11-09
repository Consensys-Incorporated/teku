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

package tech.pegasys.teku.infrastructure.ssz;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RandomSszDataGenerator {
  private final Supplier<SszBit> bitSupplier;
  private final Supplier<SszByte> byteSupplier;
  private final Supplier<SszBoolean> booleanSupplier;
  private final Supplier<SszBytes4> bytes4Supplier;
  private final Supplier<SszUInt64> uintSupplier;
  private final Supplier<SszUInt256> uint256Supplier;
  private final Supplier<SszBytes32> bytes32Supplier;

  private final Random random;
  private final int maxListSize;

  public RandomSszDataGenerator() {
    this(new Random(1), 16 * 1024);
  }

  public RandomSszDataGenerator(final Random random, final int maxListSize) {
    this.random = random;
    this.maxListSize = maxListSize;
    bitSupplier = () -> SszBit.of(random.nextBoolean());
    byteSupplier = () -> SszByte.of(random.nextInt());
    booleanSupplier = () -> SszBoolean.of(random.nextBoolean());
    bytes4Supplier = () -> SszBytes4.of(Bytes4.rightPad(Bytes.random(4, random)));
    uintSupplier = () -> SszUInt64.of(UInt64.fromLongBits(random.nextLong()));
    uint256Supplier = () -> SszUInt256.of(UInt256.fromBytes(Bytes32.random(random)));
    bytes32Supplier = () -> SszBytes32.of(Bytes32.random(random));
  }

  public RandomSszDataGenerator withMaxListSize(final int maxListSize) {
    return new RandomSszDataGenerator(random, maxListSize);
  }

  public <T extends SszData> T randomData(final SszSchema<T> schema) {
    return randomDataStream(schema).findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  public <T extends SszData> Stream<T> randomDataStream(final SszSchema<T> schema) {
    if (schema instanceof AbstractSszPrimitiveSchema) {
      if (schema.equals(SszPrimitiveSchemas.NONE_SCHEMA)) {
        return (Stream<T>) Stream.generate(() -> SszNone.INSTANCE);
      } else if (schema.equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
        return (Stream<T>) Stream.generate(bitSupplier);
      } else if (schema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)) {
        return (Stream<T>) Stream.generate(byteSupplier);
      } else if (schema.equals(SszPrimitiveSchemas.BOOLEAN_SCHEMA)) {
        return (Stream<T>) Stream.generate(booleanSupplier);
      } else if (schema.equals(SszPrimitiveSchemas.UINT64_SCHEMA)) {
        return (Stream<T>) Stream.generate(uintSupplier);
      } else if (schema.equals(SszPrimitiveSchemas.UINT256_SCHEMA)) {
        return (Stream<T>) Stream.generate(uint256Supplier);
      } else if (schema.equals(SszPrimitiveSchemas.BYTES4_SCHEMA)) {
        return (Stream<T>) Stream.generate(bytes4Supplier);
      } else if (schema.equals(SszPrimitiveSchemas.BYTES32_SCHEMA)) {
        return (Stream<T>) Stream.generate(bytes32Supplier);
      } else {
        throw new IllegalArgumentException("Unknown primitive schema: " + schema);
      }
    } else if (schema instanceof AbstractSszContainerSchema<?> containerSchema) {
      return Stream.generate(
          () -> {
            List<SszData> children =
                containerSchema.getFieldSchemas().stream()
                    .map(this::randomData)
                    .collect(Collectors.toList());
            return (T) containerSchema.createFromFieldValues(children);
          });
    } else if (schema instanceof SszCollectionSchema) {
      return Stream.generate(
          () -> {
            SszCollectionSchema<SszData, ?> collectionSchema =
                (SszCollectionSchema<SszData, ?>) schema;
            SszSchema<SszData> elementSchema = collectionSchema.getElementSchema();
            long maxChildrenToAdd;
            if (schema instanceof SszListSchema) {
              maxChildrenToAdd = Math.min(collectionSchema.getMaxLength(), maxListSize);
            } else {
              maxChildrenToAdd = collectionSchema.getMaxLength();
            }
            List<SszData> children =
                Stream.generate(() -> randomData(elementSchema))
                    .limit(maxChildrenToAdd)
                    .collect(Collectors.toList());
            SszCollection<SszData> ret = collectionSchema.createFromElements(children);
            return (T) ret;
          });
    } else if (schema instanceof SszUnionSchema<?> unionSchema) {
      return Stream.generate(
          () -> {
            int selector = random.nextInt(unionSchema.getTypesCount());
            return (T)
                unionSchema.createFromValue(
                    selector, randomData(unionSchema.getChildSchema(selector)));
          });
    } else if (schema instanceof SszOptionalSchema) {
      return Stream.generate(
          () -> {
            final SszOptionalSchema<SszData, ?> optionalSchema =
                (SszOptionalSchema<SszData, ?>) schema;
            final boolean isPresent = random.nextBoolean();
            return (T)
                optionalSchema.createFromValue(
                    isPresent
                        ? Optional.of(randomData(optionalSchema.getChildSchema()))
                        : Optional.empty());
          });

    } else {
      throw new IllegalArgumentException("Unknown schema: " + schema);
    }
  }
}
