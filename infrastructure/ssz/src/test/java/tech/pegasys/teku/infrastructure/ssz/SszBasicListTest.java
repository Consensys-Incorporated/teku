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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszBasicListTest {

  @Test
  public void simpleUInt64ListTest() {
    SszListSchema<SszUInt64, ?> listType =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 7);
    SszMutableList<SszUInt64> listView = listType.getDefault().createWritableCopy();
    TreeNode n0 = listView.commitChanges().getBackingNode();
    listView.append(SszUInt64.of(UInt64.valueOf(0x111)));
    TreeNode n1 = listView.commitChanges().getBackingNode();
    listView.append(SszUInt64.of(UInt64.valueOf(0x222)));
    listView.append(SszUInt64.of(UInt64.valueOf(0x333)));
    listView.append(SszUInt64.of(UInt64.valueOf(0x444)));
    TreeNode n2 = listView.commitChanges().getBackingNode();
    listView.append(SszUInt64.of(UInt64.valueOf(0x555)));
    TreeNode n3 = listView.commitChanges().getBackingNode();
    listView.append(SszUInt64.of(UInt64.valueOf(0x666)));
    listView.append(SszUInt64.of(UInt64.valueOf(0x777)));
    TreeNode n4 = listView.commitChanges().getBackingNode();
    listView.set(0, SszUInt64.of(UInt64.valueOf(0x800)));
    TreeNode n5 = listView.commitChanges().getBackingNode();
    listView.set(1, SszUInt64.of(UInt64.valueOf(0x801)));
    listView.set(2, SszUInt64.of(UInt64.valueOf(0x802)));
    listView.set(3, SszUInt64.of(UInt64.valueOf(0x803)));
    TreeNode n6 = listView.commitChanges().getBackingNode();

    Assertions.assertThat(listType.createFromBackingNode(n0).size()).isEqualTo(0);
    Assertions.assertThat(listType.createFromBackingNode(n1).size()).isEqualTo(1);
    Assertions.assertThat(listType.createFromBackingNode(n1).get(0).longValue()).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n2).size()).isEqualTo(4);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(0).longValue()).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(1).longValue()).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(2).longValue()).isEqualTo(0x333);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(3).longValue()).isEqualTo(0x444);
    Assertions.assertThat(listType.createFromBackingNode(n3).size()).isEqualTo(5);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(0).longValue()).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(1).longValue()).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(2).longValue()).isEqualTo(0x333);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(3).longValue()).isEqualTo(0x444);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(4).longValue()).isEqualTo(0x555);
    Assertions.assertThat(listType.createFromBackingNode(n4).size()).isEqualTo(7);
    Assertions.assertThat(listType.createFromBackingNode(n4).get(5).longValue()).isEqualTo(0x666);
    Assertions.assertThat(listType.createFromBackingNode(n4).get(6).longValue()).isEqualTo(0x777);
    Assertions.assertThat(listType.createFromBackingNode(n5).get(0).longValue()).isEqualTo(0x800);
    Assertions.assertThat(listType.createFromBackingNode(n5).get(1).longValue()).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n6).size()).isEqualTo(7);
    Assertions.assertThat(listType.createFromBackingNode(n6).get(0).longValue()).isEqualTo(0x800);
    Assertions.assertThat(listType.createFromBackingNode(n6).get(1).longValue()).isEqualTo(0x801);
    Assertions.assertThat(listType.createFromBackingNode(n6).get(2).longValue()).isEqualTo(0x802);
    Assertions.assertThat(listType.createFromBackingNode(n6).get(3).longValue()).isEqualTo(0x803);
    Assertions.assertThat(listType.createFromBackingNode(n6).get(4).longValue()).isEqualTo(0x555);

    assertThatExceptionOfType(IndexOutOfBoundsException.class)
        .isThrownBy(
            () ->
                listType
                    .createFromBackingNode(n3)
                    .createWritableCopy()
                    .set(7, SszUInt64.of(UInt64.valueOf(0xaaa))));
    assertThatExceptionOfType(IndexOutOfBoundsException.class)
        .isThrownBy(() -> listType.createFromBackingNode(n3).get(7));
    assertThatExceptionOfType(IndexOutOfBoundsException.class)
        .isThrownBy(() -> listType.createFromBackingNode(n3).get(8));
    assertThatExceptionOfType(IndexOutOfBoundsException.class)
        .isThrownBy(() -> listView.set(7, SszUInt64.of(UInt64.valueOf(0xaaa))));
    assertThatExceptionOfType(IndexOutOfBoundsException.class)
        .isThrownBy(() -> listView.append(SszUInt64.of(UInt64.valueOf(0xaaa))));

    listView.clear();
    assertThat(listView.commitChanges().hashTreeRoot()).isEqualTo(n0.hashTreeRoot());
  }
}
