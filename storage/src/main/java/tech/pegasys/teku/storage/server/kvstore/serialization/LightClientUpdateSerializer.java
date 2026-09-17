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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;

class LightClientUpdateSerializer implements KvStoreSerializer<StoredLightClientUpdate> {

  private static final int SLOT_SIZE = Long.BYTES;
  private static final int ROOT_SIZE = Bytes32.SIZE;
  private static final int PREFIX_SIZE = SLOT_SIZE + ROOT_SIZE;

  private final Spec spec;

  LightClientUpdateSerializer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public StoredLightClientUpdate deserialize(final byte[] data) {
    final Bytes bytes = Bytes.wrap(data);
    final UInt64 attestedSlot = UInt64.fromLongBits(bytes.getLong(0));
    final Bytes32 signatureBlockRoot = Bytes32.wrap(bytes.slice(SLOT_SIZE, ROOT_SIZE));
    return new StoredLightClientUpdate(
        spec.deserializeUpdate(bytes.slice(PREFIX_SIZE), attestedSlot), signatureBlockRoot);
  }

  @Override
  public byte[] serialize(final StoredLightClientUpdate value) {
    final UInt64 attestedSlot = value.update().getAttestedHeader().getBeacon().getSlot();
    return Bytes.concatenate(
            Bytes.ofUnsignedLong(attestedSlot.longValue()),
            value.signatureBlockRoot(),
            value.update().sszSerialize())
        .toArrayUnsafe();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LightClientUpdateSerializer that = (LightClientUpdateSerializer) o;
    return Objects.equals(spec, that.spec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spec);
  }
}
