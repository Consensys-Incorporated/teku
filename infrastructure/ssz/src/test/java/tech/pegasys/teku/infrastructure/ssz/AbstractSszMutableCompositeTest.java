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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszMutableCompositeTest {

  @Test
  @SuppressWarnings("unchecked")
  void setChildAfterAcquiringItByRefShouldWork() {
    SszBytes32VectorSchema<SszBytes32Vector> childSchema = SszBytes32VectorSchema.create(3);
    SszVectorSchema<SszBytes32Vector, ?> compositeSchema = SszVectorSchema.create(childSchema, 2);

    SszMutableRefVector<SszBytes32Vector, SszMutableBytes32Vector> mutableComposite =
        (SszMutableRefVector<SszBytes32Vector, SszMutableBytes32Vector>)
            compositeSchema.getDefault().createWritableCopy();
    SszMutableBytes32Vector mutableChild = mutableComposite.getByRef(0);
    mutableChild.setElement(0, Bytes32.fromHexStringLenient("0x1111"));

    SszBytes32Vector newChild =
        childSchema.of(
            Bytes32.fromHexStringLenient("0x2222"),
            Bytes32.fromHexStringLenient("0x3333"),
            Bytes32.fromHexStringLenient("0x4444"));
    mutableComposite.set(0, newChild);

    SszVector<SszBytes32Vector> changedComposite = mutableComposite.commitChanges();

    assertThat(changedComposite.get(0).getElement(0))
        .isEqualTo(Bytes32.fromHexStringLenient("0x2222"));
    assertThat(changedComposite.get(0).getElement(1))
        .isEqualTo(Bytes32.fromHexStringLenient("0x3333"));
    assertThat(changedComposite.get(0).getElement(2))
        .isEqualTo(Bytes32.fromHexStringLenient("0x4444"));
    assertThat(changedComposite.get(1).getElement(0)).isEqualTo(Bytes32.ZERO);
    assertThat(changedComposite.get(1).getElement(1)).isEqualTo(Bytes32.ZERO);
    assertThat(changedComposite.get(1).getElement(2)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  void changesFarApartAndOutOfOrderShouldBeReadBackAndCommittedInIndexOrder() {
    final SszUInt64ListSchema<SszUInt64List> schema = SszUInt64ListSchema.create(4096);
    final List<UInt64> expected =
        new ArrayList<>(LongStream.range(0, 1000).mapToObj(UInt64::valueOf).toList());
    final SszMutableUInt64List mutableList = schema.of(expected).createWritableCopy();

    // Neighbours, far apart elements and both ends, not in index order, one of them set twice
    final int[] changedIndices = {999, 0, 63, 64, 65, 500, 127, 128, 64, 1};
    for (int i = 0; i < changedIndices.length; i++) {
      final UInt64 newValue = UInt64.valueOf(1_000_000 + i);
      mutableList.setElement(changedIndices[i], newValue);
      expected.set(changedIndices[i], newValue);
    }

    assertThat(mutableList.asListUnboxed()).isEqualTo(expected);

    final SszUInt64List committed = mutableList.commitChanges();

    assertThat(committed.asListUnboxed()).isEqualTo(expected);
    assertThat(committed.hashTreeRoot()).isEqualTo(schema.of(expected).hashTreeRoot());
  }

  @Test
  void appendedElementsShouldBeReadBackAndCommitted() {
    final SszUInt64ListSchema<SszUInt64List> schema = SszUInt64ListSchema.create(4096);
    final List<UInt64> expected =
        new ArrayList<>(LongStream.range(0, 10).mapToObj(UInt64::valueOf).toList());
    final SszMutableUInt64List mutableList = schema.of(expected).createWritableCopy();

    for (int i = 0; i < 300; i++) {
      final UInt64 newValue = UInt64.valueOf(2_000_000 + i);
      mutableList.appendElement(newValue);
      expected.add(newValue);
    }

    assertThat(mutableList.size()).isEqualTo(310);
    assertThat(mutableList.asListUnboxed()).isEqualTo(expected);

    final SszUInt64List committed = mutableList.commitChanges();

    assertThat(committed.asListUnboxed()).isEqualTo(expected);
    assertThat(committed.hashTreeRoot()).isEqualTo(schema.of(expected).hashTreeRoot());
  }

  @Test
  void clearShouldDropTheChangesMadeBeforeIt() {
    final SszUInt64ListSchema<SszUInt64List> schema = SszUInt64ListSchema.create(4096);
    final SszMutableUInt64List mutableList =
        schema.of(LongStream.range(0, 200).mapToObj(UInt64::valueOf).toList()).createWritableCopy();

    mutableList.setElement(150, UInt64.valueOf(7));
    mutableList.clear();
    mutableList.appendElement(UInt64.valueOf(9));

    assertThat(mutableList.asListUnboxed()).containsExactly(UInt64.valueOf(9));

    final SszUInt64List committed = mutableList.commitChanges();

    assertThat(committed.hashTreeRoot()).isEqualTo(schema.of(UInt64.valueOf(9)).hashTreeRoot());
  }

  @Test
  @SuppressWarnings("unchecked")
  void childrenChangedByRefAndByValueShouldBeCommittedTogether() {
    final SszBytes32VectorSchema<SszBytes32Vector> childSchema = SszBytes32VectorSchema.create(2);
    final SszVectorSchema<SszBytes32Vector, ?> compositeSchema =
        SszVectorSchema.create(childSchema, 200);
    final Bytes32 byRefValue = Bytes32.fromHexStringLenient("0x1111");
    final Bytes32 byValueValue = Bytes32.fromHexStringLenient("0x2222");

    final SszMutableRefVector<SszBytes32Vector, SszMutableBytes32Vector> mutableComposite =
        (SszMutableRefVector<SszBytes32Vector, SszMutableBytes32Vector>)
            compositeSchema.getDefault().createWritableCopy();
    mutableComposite.getByRef(170).setElement(1, byRefValue);
    mutableComposite.set(3, childSchema.of(byValueValue, byValueValue));
    // The same child again: its earlier change is kept
    mutableComposite.getByRef(170).setElement(0, byRefValue);

    assertThat(mutableComposite.get(170).getElement(1)).isEqualTo(byRefValue);

    final SszVector<SszBytes32Vector> committed = mutableComposite.commitChanges();

    for (int i = 0; i < 200; i++) {
      final Bytes32 expectedValue = i == 170 ? byRefValue : i == 3 ? byValueValue : Bytes32.ZERO;
      assertThat(committed.get(i).getElement(0)).isEqualTo(expectedValue);
      assertThat(committed.get(i).getElement(1)).isEqualTo(expectedValue);
    }
  }
}
