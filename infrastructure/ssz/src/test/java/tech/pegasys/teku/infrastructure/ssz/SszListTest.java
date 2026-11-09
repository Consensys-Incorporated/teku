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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSubContainer;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszListSchemaTestBase;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszListTest implements SszListTestBase, SszMutableRefCompositeTestBase {

  private final RandomSszDataGenerator randomSsz =
      new RandomSszDataGenerator().withMaxListSize(256);

  @Override
  public Stream<SszList<?>> sszData() {
    return SszListSchemaTestBase.complexListSchemas()
        .flatMap(
            listSchema -> Stream.of(listSchema.getDefault(), randomSsz.randomData(listSchema)));
  }

  @Test
  void testMutableListReusable() {
    List<TestSubContainer> elements =
        IntStream.range(0, 5)
            .mapToObj(i -> new TestSubContainer(UInt64.valueOf(i), Bytes32.leftPad(Bytes.of(i))))
            .collect(Collectors.toList());

    SszListSchema<TestSubContainer, ?> type =
        SszListSchema.create(TestContainers.TestSubContainer.SSZ_SCHEMA, 100);
    SszList<TestSubContainer> lr1 = type.getDefault();
    SszMutableList<TestSubContainer> lw1 = lr1.createWritableCopy();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.append(elements.get(0));
    SszMutableList<TestSubContainer> lw2 = type.getDefault().createWritableCopy();
    lw2.append(elements.get(0));
    SszList<TestSubContainer> lr2 = lw2.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    lw1.appendAll(elements.subList(1, 5));
    SszMutableList<TestSubContainer> lw3 = type.getDefault().createWritableCopy();
    lw3.appendAll(elements);
    SszList<TestSubContainer> lr3 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr3.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr3.hashTreeRoot());

    lw1.clear();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.appendAll(elements.subList(0, 5));
    SszMutableList<TestSubContainer> lw4 = type.getDefault().createWritableCopy();
    lw4.appendAll(elements);
    SszList<TestSubContainer> lr4 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr4.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr4.hashTreeRoot());

    lw1.clear();
    lw1.append(elements.get(0));

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());
  }
}
