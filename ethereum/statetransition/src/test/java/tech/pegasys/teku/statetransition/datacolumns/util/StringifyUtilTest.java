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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class StringifyUtilTest {

  record TestCase(IntStream indices, String expectedString) {
    @Override
    public String toString() {
      return expectedString;
    }
  }

  static final int MAX_INDICES_LEN = 128;

  static final List<TestCase> TEST_CASES =
      List.of(
          new TestCase(IntStream.empty(), "[]"),
          new TestCase(range(0, 128), "[all]"),
          new TestCase(range(0, 128).skip(1), "[all except 0]"),
          new TestCase(range(0, 128).limit(127), "[all except 127]"),
          new TestCase(
              range(0, 128).filter(i -> !Set.of(14, 16, 18, 98).contains(i)),
              "[all except 14,16,18,98]"),
          new TestCase(IntStream.of(14, 16, 18, 98), "[14,16,18,98]"),
          new TestCase(IntStream.of(14, 15, 18, 98), "[14,15,18,98]"),
          new TestCase(IntStream.of(14, 15, 16, 98), "[14..16,98]"),
          new TestCase(range(0, 128).skip(64), "[64..127]"),
          new TestCase(range(0, 128).limit(64), "[0..63]"),
          new TestCase(
              range(0, 128).filter(i -> i % 3 != 0),
              "[bitmap: 0xb66ddbb66ddbb66ddbb66ddbb66ddbb6]"),
          new TestCase(
              range(10, 100).filter(i -> !Set.of(14, 16, 18, 98).contains(i)),
              "[10..13,15,17,19..97,99]"));

  private static Stream<Arguments> provideTestCaseParameters() {
    return TEST_CASES.stream().map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("provideTestCaseParameters")
  void columnIndicesToString_test(final TestCase testCase) {
    final List<Integer> idxList = testCase.indices.boxed().toList();
    final String s = StringifyUtil.columnIndicesToString(idxList, MAX_INDICES_LEN);

    assertThat(s).isEqualTo("(len: " + idxList.size() + ") " + testCase.expectedString);
  }
}
