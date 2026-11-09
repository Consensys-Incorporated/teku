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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES_SERIALIZER;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes;

public class BytesSerializerPropertyTest {
  @Property
  public void roundTrip(@ForAll @Size(max = 10000) final byte[] bytes) {
    Bytes value = Bytes.wrap(bytes);
    final byte[] serialized = BYTES_SERIALIZER.serialize(value);
    final Bytes deserialized = BYTES_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }
}
