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

package tech.pegasys.teku.api.provider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GenesisData {
  @JsonProperty("genesis_time")
  @Schema(
      type = "string",
      example = "1590832934",
      description =
          "The genesis_time configured for the beacon node, which is the unix time in seconds at which the Eth2.0 chain began.")
  public final UInt64 genesisTime;

  @JsonProperty("genesis_validators_root")
  @Schema(
      type = "string",
      example = "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2",
      pattern = "^0x[a-fA-F0-9]{64}$")
  public final Bytes32 genesisValidatorsRoot;

  @JsonProperty("genesis_fork_version")
  @Schema(type = "string", example = "0x00000000", pattern = "^0x[a-fA-F0-9]{8}$")
  public final Bytes4 genesisForkVersion;

  @JsonCreator
  public GenesisData(
      @JsonProperty("genesis_time") final UInt64 genesisTime,
      @JsonProperty("genesis_validators_root") final Bytes32 genesisValidatorsRoot,
      @JsonProperty("genesis_fork_version") final Bytes4 genesisForkVersion) {
    this.genesisTime = genesisTime;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    this.genesisForkVersion = genesisForkVersion;
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
        && Objects.equals(genesisValidatorsRoot, that.genesisValidatorsRoot)
        && Objects.equals(genesisForkVersion, that.genesisForkVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(genesisTime, genesisValidatorsRoot, genesisForkVersion);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("genesisTime", genesisTime)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .add("genesisForkVersion", genesisForkVersion)
        .toString();
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return genesisValidatorsRoot;
  }
}
