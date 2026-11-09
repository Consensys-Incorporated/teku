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

package tech.pegasys.teku.validator.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.validator.api.Bytes32Parser.toBytes32;

import org.junit.jupiter.api.Test;

public class Bytes32ParserTest {

  @Test
  void testStringAndBytesArgsAreEquivalent() throws Exception {
    assertThat(toBytes32("some bytes")).isEqualTo(toBytes32("some bytes".getBytes(UTF_8)));
  }

  @Test
  void testLengthGreaterThan32() throws Exception {
    assertThatThrownBy(() -> toBytes32("the quick brown fox jumps over the lazy dog"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
