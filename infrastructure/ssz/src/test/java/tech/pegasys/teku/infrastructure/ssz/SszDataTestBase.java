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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

@TestInstance(Lifecycle.PER_CLASS)
public interface SszDataTestBase {

  // workaround for https://github.com/junit-team/junit5/issues/1477
  static Stream<Arguments> passWhenEmpty(final Stream<Arguments> args) {
    List<Arguments> list = args.toList();
    Assumptions.assumeFalse(list.isEmpty());
    return list.stream();
  }

  Stream<? extends SszData> sszData();

  default Stream<? extends SszData> sszWritableData() {
    return sszData().filter(SszData::isWritableSupported);
  }

  default Stream<? extends SszData> sszNonWritableData() {
    return sszData().filter(d -> !d.isWritableSupported());
  }

  default Stream<Arguments> sszWritableDataArguments() {
    return passWhenEmpty(sszWritableData().map(Arguments::of));
  }

  default Stream<Arguments> sszNonWritableDataArguments() {
    return passWhenEmpty(sszNonWritableData().map(Arguments::of));
  }

  default Stream<Arguments> sszDataArguments() {
    return sszData().map(Arguments::of);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void sszSerialize_testSszRoundtrip(final SszData data) {
    Bytes ssz = data.sszSerialize();
    SszData data1 = data.getSchema().sszDeserialize(ssz);
    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(data);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getBackingNode_testTreeRoundtrip(final SszData data) {
    TreeNode tree = data.getBackingNode();
    SszData data1 = data.getSchema().createFromBackingNode(tree);
    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(data);
  }

  @MethodSource("sszWritableDataArguments")
  @ParameterizedTest
  default void createWritableCopy_commitShouldReturnEqualInstance(final SszData data) {
    SszMutableData writableCopy = data.createWritableCopy();
    SszData data1 = writableCopy.commitChanges();
    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(data);
  }

  @MethodSource("sszWritableDataArguments")
  @ParameterizedTest
  default void createWritableCopy_shouldBeSszEqualToOriginal(final SszData data) {
    SszMutableData writableCopy = data.createWritableCopy();
    SszDataAssert.assertThatSszData((SszData) writableCopy)
        .isEqualBySszTo(data)
        .isEqualByHashTreeRootTo(data)
        .isEqualByGettersTo(data);
  }

  @MethodSource("sszNonWritableDataArguments")
  @ParameterizedTest
  default void createWritableCopy_shouldThrowUnsupported(final SszData data) {
    assertThatThrownBy(data::createWritableCopy).isInstanceOf(UnsupportedOperationException.class);
  }

  @MethodSource("sszNonWritableDataArguments")
  @ParameterizedTest
  default void isWritableSupported_shouldReturnFalse(final SszData data) {
    assertThat(data.isWritableSupported()).isFalse();
  }

  @MethodSource("sszWritableDataArguments")
  @ParameterizedTest
  default void isWritableSupported_shouldReturnTrue(final SszData data) {
    assertThat(data.isWritableSupported()).isTrue();
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getSchema_shouldBeTheSame(final SszData data) {
    Assertions.assertThat(data.getSchema()).isSameAs(data.getSchema());
    Assertions.assertThat(data.getSchema().getDefault().getSchema()).isSameAs(data.getSchema());
    if (data.isWritableSupported()) {
      Assertions.assertThat(data.createWritableCopy().getSchema()).isSameAs(data.getSchema());
      Assertions.assertThat(data.createWritableCopy().commitChanges().getSchema())
          .isSameAs(data.getSchema());
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void hashTreeRoot_shouldBeEqual(final SszData data) {
    assertThat(data.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    if (data.isWritableSupported()) {
      assertThat(data.createWritableCopy().hashTreeRoot()).isEqualTo(data.hashTreeRoot());
      assertThat(data.createWritableCopy().commitChanges().hashTreeRoot())
          .isEqualTo(data.hashTreeRoot());
    }
  }

  @MethodSource("sszWritableDataArguments")
  @ParameterizedTest
  default void clear_shouldYieldDefault(final SszData data) {
    SszMutableData mutableData = data.createWritableCopy();
    mutableData.clear();
    SszData data1 = mutableData.commitChanges();
    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(data.getSchema().getDefault());
  }
}
