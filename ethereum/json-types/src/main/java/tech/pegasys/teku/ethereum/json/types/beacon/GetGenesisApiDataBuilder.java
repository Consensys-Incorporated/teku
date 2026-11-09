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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetGenesisApiDataBuilder {
  public static final DeserializableTypeDefinition<GetGenesisApiData> GET_GENESIS_API_DATA_TYPE =
      withDataWrapper(
          "GetGenesisResponse",
          DeserializableTypeDefinition.object(
                  GetGenesisApiData.class, GetGenesisApiDataBuilder.class)
              .initializer(GetGenesisApiDataBuilder::new)
              .finisher(GetGenesisApiDataBuilder::build)
              .withField(
                  "genesis_time",
                  UINT64_TYPE,
                  GetGenesisApiData::getGenesisTime,
                  GetGenesisApiDataBuilder::genesisTime)
              .withField(
                  "genesis_validators_root",
                  BYTES32_TYPE,
                  GetGenesisApiData::getGenesisValidatorsRoot,
                  GetGenesisApiDataBuilder::genesisValidatorsRoot)
              .withField(
                  "genesis_fork_version",
                  BYTES4_TYPE,
                  GetGenesisApiData::getGenesisForkVersion,
                  GetGenesisApiDataBuilder::genesisForkVersion)
              .build());

  private UInt64 genesisTime;
  private Bytes32 genesisValidatorsRoot;
  private Bytes4 genesisForkVersion;

  public GetGenesisApiDataBuilder genesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
    return this;
  }

  public GetGenesisApiDataBuilder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    return this;
  }

  public GetGenesisApiDataBuilder genesisForkVersion(final Bytes4 genesisForkVersion) {
    this.genesisForkVersion = genesisForkVersion;
    return this;
  }

  public GetGenesisApiData build() {
    return new GetGenesisApiData(genesisTime, genesisValidatorsRoot, genesisForkVersion);
  }
}
