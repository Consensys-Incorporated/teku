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

import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class Bytes32SetSerializerTest {

  final Set<Bytes32> inputData = Set.of(Bytes32.ZERO, Bytes32.random());

  @Test
  public void shouldPackAndUnpack() {
    final byte[] data = BLOCK_ROOTS_SERIALIZER.serialize(inputData);
    final Set<Bytes32> result = BLOCK_ROOTS_SERIALIZER.deserialize(data);
    assertThat(result).isEqualTo(inputData);
  }
}
