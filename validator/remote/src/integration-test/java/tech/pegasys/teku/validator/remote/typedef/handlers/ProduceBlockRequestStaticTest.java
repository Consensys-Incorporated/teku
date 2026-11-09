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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import okhttp3.Response;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProduceBlockRequestStaticTest {

  private static final String KEY = "key";

  static Stream<Arguments> headerParserScenarios() {
    return Stream.of(
        // Valid
        Arguments.of("Lower bound", "0", UInt256.ZERO),
        Arguments.of("Upper bound", UInt256.MAX_VALUE.toDecimalString(), UInt256.MAX_VALUE),
        Arguments.of("In range", "1000", UInt256.valueOf(1000L)),
        // NumberFormatException
        Arguments.of("Not a number", KEY, UInt256.ZERO),
        // IllegalArgumentException
        Arguments.of("Negative value", "-1", UInt256.ZERO),
        Arguments.of("Beyond Max", UInt256.MAX_VALUE.toDecimalString() + "0", UInt256.ZERO));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("headerParserScenarios")
  void parseHeader(final String label, final String value, final UInt256 expectedResult) {
    final Response response = mock(Response.class);
    when(response.header(KEY)).thenReturn(value);
    assertThat(ProduceBlockRequest.parseUInt256Header(response, KEY)).isEqualTo(expectedResult);
  }
}
