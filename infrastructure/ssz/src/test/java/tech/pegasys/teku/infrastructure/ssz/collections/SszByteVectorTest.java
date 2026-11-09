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

package tech.pegasys.teku.infrastructure.ssz.collections;

import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;

public class SszByteVectorTest implements SszByteVectorTestBase {
  private final RandomSszDataGenerator generator = new RandomSszDataGenerator();

  public Stream<SszByteVectorSchema<?>> sszSchemas() {
    return Stream.of(
        SszByteVectorSchema.create(1),
        SszByteVectorSchema.create(31),
        SszByteVectorSchema.create(32),
        SszByteVectorSchema.create(33),
        SszByteVectorSchema.create(254),
        SszByteVectorSchema.create(255),
        SszByteVectorSchema.create(256));
  }

  @Override
  public Stream<SszByteVector> sszData() {
    return sszSchemas()
        .flatMap(schema -> Stream.of(schema.getDefault(), generator.randomData(schema)));
  }
}
