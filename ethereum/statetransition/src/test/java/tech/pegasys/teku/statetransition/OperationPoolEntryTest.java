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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.MessageWithValidatorId;

public class OperationPoolEntryTest {
  private final TestClass b0 = TestClass.of(Bytes4.fromHexString("0xFFFFFF00"));
  private final TestClass b1 = TestClass.of(Bytes4.fromHexString("0xFFFFFF11"));
  private final TestClass b2 = TestClass.of(Bytes4.fromHexString("0xFFFFFF22"));

  private final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);

  @Test
  void shouldSortLocalFirst() {
    final List<OperationPoolEntry<TestClass>> list = new ArrayList<>();

    list.add(new OperationPoolEntry<>(b2, true, timeProvider.getTimeInSeconds()));
    list.add(new OperationPoolEntry<>(b0, false, timeProvider.getTimeInSeconds()));
    list.add(new OperationPoolEntry<>(b1, true, timeProvider.getTimeInSeconds()));

    assertThat(
            list.stream().sorted().map(OperationPoolEntry::getMessage).collect(Collectors.toList()))
        .containsExactly(b2, b1, b0);
  }

  private static class TestClass extends AbstractSszPrimitive<Bytes4>
      implements MessageWithValidatorId {

    TestClass(final Bytes4 b) {
      super(b, SszPrimitiveSchemas.BYTES4_SCHEMA);
    }

    static TestClass of(final Bytes4 b) {
      return new TestClass(b);
    }

    @Override
    public UInt64 getValidatorId() {
      return UInt64.fromLongBits(Integer.toUnsignedLong(this.get().getWrappedBytes().toInt()));
    }
  }
}
