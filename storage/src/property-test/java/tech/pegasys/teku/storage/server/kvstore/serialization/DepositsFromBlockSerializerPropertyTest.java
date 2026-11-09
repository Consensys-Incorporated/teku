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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER;

import java.util.stream.LongStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositsFromBlockSerializerPropertyTest {
  static final long MAX_DEPOSITS = 1000L;

  @Property(tries = 100)
  public boolean roundTrip(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) final Spec spec,
      @ForAll final long blockNumber,
      @ForAll @Size(32) final byte[] blockHash,
      @ForAll final long blockTimestamp,
      @ForAll @LongRange(min = 0, max = Long.MAX_VALUE - MAX_DEPOSITS) final long startIndex,
      @ForAll @LongRange(min = 1, max = MAX_DEPOSITS) final long depositCount) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final DepositsFromBlockEvent value =
        DepositsFromBlockEvent.create(
            UInt64.fromLongBits(blockNumber),
            Bytes32.wrap(blockHash),
            UInt64.fromLongBits(blockTimestamp),
            LongStream.range(startIndex, startIndex + depositCount)
                .mapToObj(dataStructureUtil::randomDepositEvent));
    final byte[] serialized = DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER.serialize(value);
    final DepositsFromBlockEvent deserialized =
        DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER.deserialize(serialized);
    return deserialized.equals(value);
  }
}
