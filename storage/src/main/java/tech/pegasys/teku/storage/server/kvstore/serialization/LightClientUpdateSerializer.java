/*
 * Copyright Consensys Software Inc., 2026
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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

class LightClientUpdateSerializer implements KvStoreSerializer<LightClientUpdate> {

  private final Spec spec;

  public LightClientUpdateSerializer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public LightClientUpdate deserialize(final byte[] data) {
    final Bytes bytesData = Bytes.wrap(data);
    return spec.deserializeUpdate(bytesData, extractAttestedHeaderSlot(bytesData));
  }

  @Override
  public byte[] serialize(final LightClientUpdate value) {
    return value.sszSerialize().toArrayUnsafe();
  }

  private static UInt64 extractAttestedHeaderSlot(final Bytes bytes) {
    return SszPrimitiveSchemas.UINT64_SCHEMA
        .sszDeserialize(bytes.slice(0, SszPrimitiveSchemas.UINT64_SCHEMA.getSszFixedPartSize()))
        .get();
  }
}
