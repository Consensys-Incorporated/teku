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

package tech.pegasys.teku.spec.datastructures.genesis;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GenesisData {
  private final UInt64 genesisTime;
  private final Bytes32 genesisValidatorsRoot;

  public GenesisData(final UInt64 genesisTime, final Bytes32 genesisValidatorsRoot) {
    this.genesisTime = genesisTime;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return genesisValidatorsRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GenesisData that = (GenesisData) o;
    return Objects.equals(genesisTime, that.genesisTime)
        && Objects.equals(genesisValidatorsRoot, that.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(genesisTime, genesisValidatorsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("genesisTime", genesisTime)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private UInt64 genesisTime;
    private Bytes32 genesisValidatorsRoot;

    private Builder() {}

    public Builder genesisTime(final UInt64 genesisTime) {
      this.genesisTime = genesisTime;
      return this;
    }

    public Builder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
      this.genesisValidatorsRoot = genesisValidatorsRoot;
      return this;
    }

    public GenesisData build() {
      return new GenesisData(genesisTime, genesisValidatorsRoot);
    }
  }
}
