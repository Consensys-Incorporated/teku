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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class BeaconStateSerializer implements KvStoreSerializer<BeaconState> {

  private final Spec spec;

  BeaconStateSerializer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public BeaconState deserialize(final byte[] data) {
    return spec.deserializeBeaconState(Bytes.wrap(data));
  }

  @Override
  public byte[] serialize(final BeaconState value) {
    return value.sszSerialize().toArrayUnsafe();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconStateSerializer that = (BeaconStateSerializer) o;
    return Objects.equals(spec, that.spec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spec);
  }
}
