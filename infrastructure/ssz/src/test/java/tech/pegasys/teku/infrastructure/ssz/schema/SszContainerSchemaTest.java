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

import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestLargeContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSmallContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSubContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.VariableSizeContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableSubContainer;

public class SszContainerSchemaTest extends SszCompositeSchemaTestBase {

  public static Stream<SszContainerSchema<?>> testContainerSchemas() {
    return Stream.of(
        TestLargeContainer.SSZ_SCHEMA,
        TestSubContainer.SSZ_SCHEMA,
        TestSmallContainer.SSZ_SCHEMA,
        VariableSizeContainer.SSZ_SCHEMA,
        WritableContainer.SSZ_SCHEMA,
        WritableSubContainer.SSZ_SCHEMA);
  }

  @Override
  public Stream<SszContainerSchema<?>> testSchemas() {
    return testContainerSchemas();
  }
}
