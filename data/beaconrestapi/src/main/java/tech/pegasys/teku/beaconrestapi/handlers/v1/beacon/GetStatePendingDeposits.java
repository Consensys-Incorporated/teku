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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetStatePendingDeposits extends AbstractGetStateField<SszList<PendingDeposit>> {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/pending_deposits";

  public GetStatePendingDeposits(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  GetStatePendingDeposits(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        provider,
        EndpointMetadata.get(ROUTE)
            .operationId("getPendingDeposits")
            .summary("Get pending deposits from state")
            .description(
                "Returns pending deposits for state with given 'stateId'. Should return 400 if requested before electra."),
        "GetPendingDepositsResponse",
        SpecMilestone.ELECTRA,
        "pending deposits",
        SchemaDefinitionsElectra.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ELECTRA))
            .getPendingDepositsSchema(),
        state -> BeaconStateElectra.required(state).getPendingDeposits());
  }
}
