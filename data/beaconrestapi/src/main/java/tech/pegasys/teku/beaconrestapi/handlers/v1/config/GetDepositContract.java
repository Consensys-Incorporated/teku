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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_CONFIG;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.function.Function;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetDepositContract extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/config/deposit_contract";
  private final Eth1Address depositContractAddress;
  private final ConfigProvider configProvider;

  private static final SerializableTypeDefinition<DepositContractData> DEPOSIT_CONTRACT_TYPE =
      SerializableTypeDefinition.object(DepositContractData.class)
          .withField(
              "chain_id",
              UINT64_TYPE.withDescription("Id of Eth1 chain on which contract is deployed."),
              DepositContractData::getChainId)
          .withField("address", ETH1ADDRESS_TYPE, DepositContractData::getAddress)
          .build();

  static final SerializableTypeDefinition<DepositContractData> DEPOSIT_CONTRACT_RESPONSE_TYPE =
      SerializableTypeDefinition.object(DepositContractData.class)
          .name("GetDepositContractResponse")
          .withField("data", DEPOSIT_CONTRACT_TYPE, Function.identity())
          .build();

  public GetDepositContract(
      final Eth1Address depositContractAddress, final ConfigProvider configProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDepositContract")
            .summary("Get deposit contract address")
            .description("Retrieve deposit contract address and genesis fork version.")
            .tags(TAG_CONFIG)
            .response(SC_OK, "Request successful", DEPOSIT_CONTRACT_RESPONSE_TYPE)
            .build());
    this.configProvider = configProvider;
    this.depositContractAddress = depositContractAddress;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final long depositChainId = configProvider.getGenesisSpecConfig().getDepositChainId();
    request.respondOk(new DepositContractData(depositChainId, depositContractAddress));
  }

  static class DepositContractData {
    final UInt64 chainId;
    final Eth1Address address;

    DepositContractData(final long chainId, final Eth1Address address) {
      this.chainId = UInt64.valueOf(chainId);
      this.address = address;
    }

    UInt64 getChainId() {
      return chainId;
    }

    Eth1Address getAddress() {
      return address;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final DepositContractData that = (DepositContractData) o;
      return Objects.equals(chainId, that.chainId) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
      return Objects.hash(chainId, address);
    }
  }
}
