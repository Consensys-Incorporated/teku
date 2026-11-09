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

package tech.pegasys.teku.spec.logic.versions.deneb.types;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VersionedHash {

  public static final int SIZE = 32;

  final Bytes version;
  final Bytes value;

  private VersionedHash(final Bytes version, final Bytes value) {
    this.version = version;
    this.value = value;
  }

  public VersionedHash(final Bytes32 value) {
    this.version = value.slice(0, 1);
    this.value = value.slice(1);
  }

  public static VersionedHash create(final Bytes version, final Bytes32 hash) {
    checkArgument(version.size() == 1, "Version is 1-byte flag");
    return new VersionedHash(version, hash.slice(1));
  }

  public static VersionedHash fromHexString(final String hexVersionedHash) {
    return new VersionedHash(Bytes32.fromHexString(hexVersionedHash));
  }

  public Bytes getVersion() {
    return version;
  }

  public Bytes getValue() {
    return value;
  }

  public Bytes32 get() {
    return Bytes32.wrap(Bytes.concatenate(version, value));
  }

  public String toHexString() {
    return Bytes.wrap(version, value).toHexString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VersionedHash that = (VersionedHash) o;
    return Objects.equals(version, that.version) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, value);
  }

  @Override
  public String toString() {
    return toHexString();
  }
}
