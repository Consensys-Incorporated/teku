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

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;

public abstract class SszListSchemaTestBase extends SszCollectionSchemaTestBase {

  public static Stream<SszListSchema<?, ?>> complexListSchemas() {
    return complexElementSchemas()
        .flatMap(
            elementSchema ->
                Stream.concat(
                    Stream.of(
                        SszListSchema.create(elementSchema, 0),
                        SszListSchema.create(elementSchema, 1),
                        SszListSchema.create(elementSchema, 2),
                        SszListSchema.create(elementSchema, 3),
                        SszListSchema.create(elementSchema, 10),
                        SszListSchema.create(elementSchema, 1L << 33)),
                    createSuperNodeVariant(elementSchema)));
  }

  private static Stream<? extends SszListSchema<?, ?>> createSuperNodeVariant(
      final SszSchema<?> elementSchema) {
    // SuperNodes only support fixed sized content
    return elementSchema.isFixedSize()
        ? Stream.of(SszListSchema.create(elementSchema, 1L << 16, SszSchemaHints.sszSuperNode(8)))
        : Stream.empty();
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getChildSchema_shouldThrowIndexOutOfBounds(final SszListSchema<?, ?> schema) {}
}
