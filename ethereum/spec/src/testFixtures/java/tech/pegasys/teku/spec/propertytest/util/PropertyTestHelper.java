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

package tech.pegasys.teku.spec.propertytest.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;

public class PropertyTestHelper {
  @SuppressWarnings("unchecked")
  public static <T extends SszData, S extends SszSchema<T>> void assertRoundTrip(final T data)
      throws JsonProcessingException {
    final S schema = (S) data.getSchema();

    // Round-trip SSZ serialization.
    final Bytes ssz = data.sszSerialize();
    final T fromSsz = schema.sszDeserialize(ssz);
    assertThat(fromSsz).isEqualTo(data);

    // Round-trip JSON serialization.
    final DeserializableTypeDefinition<T> typeDefinition = schema.getJsonTypeDefinition();
    final String json = JsonUtil.serialize(data, typeDefinition);
    final T fromJson = JsonUtil.parse(json, typeDefinition);
    assertThat(fromJson).isEqualTo(data);
  }

  @SuppressWarnings("unchecked")
  public static <T extends SszData, S extends SszSchema<T>>
      void assertDeserializeMutatedThrowsExpected(final T data, final int seed) {
    final S schema = (S) data.getSchema();
    final Bytes ssz = data.sszSerialize();
    final Bytes mutated = Mutator.mutate(ssz, seed);

    try {
      schema.sszDeserialize(mutated);
    } catch (Exception e) {
      assertThat(e).isInstanceOfAny(SszDeserializeException.class, IllegalArgumentException.class);
    }
  }
}
