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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class SszTypeTestBase {

  protected abstract Stream<? extends SszSchema<?>> testSchemas();

  Stream<Arguments> testSchemaArguments() {
    return testSchemas().map(Arguments::of);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getFixedPartSize_shouldBeNonZeroForFixed(final SszType type) {
    Assumptions.assumeTrue(type.isFixedSize());
    assertThat(type.getSszFixedPartSize()).isNotZero();
  }
}
