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

import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;

public class Bytes32SetSerializer implements KvStoreSerializer<Set<Bytes32>> {
  @Override
  public Set<Bytes32> deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final Set<Bytes32> output = new HashSet<>();
          while (!reader.isComplete()) {
            output.add(Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE)));
          }
          return output;
        });
  }

  @Override
  public byte[] serialize(final Set<Bytes32> value) {
    Bytes bytes = SSZ.encode(writer -> value.forEach(writer::writeFixedBytes));
    return bytes.toArrayUnsafe();
  }
}
