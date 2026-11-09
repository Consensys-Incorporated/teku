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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_CONFIG;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class GetForkSchedule extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/config/fork_schedule";
  private final ConfigProvider configProvider;

  private static final SerializableTypeDefinition<List<Fork>> FORK_SCHEDULE_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Fork>>object()
          .name("GetForkScheduleResponse")
          .description("Retrieve all forks, past present and future, of which this node is aware.")
          .withField("data", listOf(Fork.SSZ_SCHEMA.getJsonTypeDefinition()), Function.identity())
          .build();

  public GetForkSchedule(final DataProvider dataProvider) {
    this(dataProvider.getConfigProvider());
  }

  GetForkSchedule(final ConfigProvider configProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getForkSchedule")
            .summary("Get scheduled upcoming forks.")
            .description("Retrieve all scheduled upcoming forks this node is aware of.")
            .tags(TAG_CONFIG)
            .response(SC_OK, "Success", FORK_SCHEDULE_RESPONSE_TYPE)
            .build());
    this.configProvider = configProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(configProvider.getStateForkSchedule());
  }
}
