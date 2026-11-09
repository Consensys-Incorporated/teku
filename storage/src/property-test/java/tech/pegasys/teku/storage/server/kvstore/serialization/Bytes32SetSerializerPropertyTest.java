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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BLOCK_ROOTS_SERIALIZER;

import com.google.common.primitives.Bytes;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.SetArbitrary;
import org.apache.tuweni.bytes.Bytes32;

public class Bytes32SetSerializerPropertyTest {
  @Property
  public void roundTrip(@ForAll("setOfByte32") final Set<Bytes32> value) {
    final byte[] serialized = BLOCK_ROOTS_SERIALIZER.serialize(value);
    final Set<Bytes32> deserialized = BLOCK_ROOTS_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }

  @Provide
  public SetArbitrary<Bytes32> setOfByte32() {
    return Arbitraries.bytes()
        .list()
        .ofSize(32)
        .map(Bytes::toArray)
        .map(Bytes32::wrap)
        .set()
        .ofMaxSize(1000);
  }
}
