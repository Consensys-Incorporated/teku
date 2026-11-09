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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;

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
}
